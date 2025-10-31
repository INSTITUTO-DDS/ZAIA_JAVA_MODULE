package br.com.zaya.flow.events;

import br.com.sankhya.extensions.eventoprogramavel.EventoProgramavelJava;
import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.core.JapeSession;
import br.com.sankhya.jape.dao.JdbcWrapper;
import br.com.sankhya.jape.event.ModifingFields;
import br.com.sankhya.jape.event.PersistenceEvent;
import br.com.sankhya.jape.event.TransactionContext;
import br.com.sankhya.jape.sql.NativeSql;
import br.com.sankhya.jape.util.JapeSessionContext;
import br.com.sankhya.jape.vo.DynamicVO;
import br.com.sankhya.jape.wrapper.JapeFactory;
import br.com.sankhya.jape.wrapper.JapeWrapper;
import br.com.sankhya.jape.wrapper.fluid.FluidUpdateVO;
import br.com.sankhya.modelcore.auth.AuthenticationInfo;
import br.com.sankhya.modelcore.util.EntityFacadeFactory;
import br.com.sankhya.ws.ServiceContext;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.util.PDFTextStripper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static br.com.zaya.flow.events.ApiOcr.registrarLog;

public class BoletoLinhaDigitavelExtractor implements EventoProgramavelJava {

    private static final EntityFacade dwFacade = EntityFacadeFactory.getDWFFacade();
    private static final JdbcWrapper jdbc = dwFacade.getJdbcWrapper();

    @Override
    public void afterInsert(PersistenceEvent event) throws Exception {
        DynamicVO vo = (DynamicVO) event.getVo();
        String tabela = vo.getValueObjectID().replaceAll("\\.ValueObject$", "");
        processarCodigoBarras(vo, tabela);
    }

    @Override
    public void afterUpdate(PersistenceEvent event) throws Exception {
        DynamicVO vo = (DynamicVO) event.getVo();
        ModifingFields mdf = event.getModifingFields();
        if (mdf.isModifing("BOLETO")) {
            String tabela = vo.getValueObjectID().replaceAll("\\.ValueObject$", "");
            processarCodigoBarras(vo, tabela);
        }
    }

    private static byte[] obterArquivoEmBytes(BigDecimal idInstPrn, String tabela) throws Exception {
        NativeSql qry = null;
        ResultSet rs = null;
        JapeSession.SessionHandle hnd = null;
        JdbcWrapper j = null;
        try {
            hnd = JapeSession.open();
            j = dwFacade.getJdbcWrapper();
            j.openSession();
            qry = new NativeSql(j);
            qry.setReuseStatements(false);
            qry.setNamedParameter("IDINSTPRN", idInstPrn);
            rs = qry.executeQuery("SELECT BOLETO FROM " + tabela + " WHERE IDINSTPRN = :IDINSTPRN");
            if (rs.next()) {
                return rs.getBytes("BOLETO");
            }
            return null;
        } finally {
            if (rs != null) try { rs.close(); } catch (Exception ignore) {}
            if (qry != null) try { qry.close(); } catch (Exception ignore) {}
            JdbcWrapper.closeSession(j);
            JapeSession.close(hnd);
        }
    }

    private static String sanitize(String input) {
        return input == null ? "" :
                input.replace("\r", " ")
                        .replace("\n", " ")
                        .replaceAll("\\s+", " ")
                        .trim();
    }

    private void processarCodigoBarras(DynamicVO vo, String tabela) throws Exception {
        BigDecimal idInstPrn = vo.asBigDecimal("IDINSTPRN");
        String tipoPag = vo.asString("TIPOPAG");

        if (!tipoPag.contains("B")) {
            JapeWrapper dao = JapeFactory.dao(tabela);
            FluidUpdateVO upd = dao.prepareToUpdate(vo);
            upd.set("CODBARRAS", (Object) null);
            upd.update();
            return;
        }

        AuthenticationInfo auth = new AuthenticationInfo("SUP", BigDecimal.ZERO, BigDecimal.ZERO, 0);
        auth.makeCurrent();
        ServiceContext sctx = new ServiceContext(null);
        sctx.setAutentication(auth); sctx.makeCurrent();
        JapeSessionContext.putProperty("usuario_logado", auth.getUserID());
        JapeSession.putProperty("usuario_logado", auth.getUserID());
        BigDecimal codUsu = auth.getUserID();

        byte[] pdfBytes = obterArquivoEmBytes(idInstPrn, tabela);
        if (pdfBytes == null || pdfBytes.length == 0) {
            registrarLog("Erro", "PDF não encontrado em " + tabela, null, "Boleto", codUsu, idInstPrn);
            updateData(vo, tabela, null, "PDF não encontrado");
            return;
        }

        String codigo = null;
        PDDocument document = null;
        String textoPdf = "";

        try {
            PDFParser parser = new PDFParser(new ByteArrayInputStream(pdfBytes));
            parser.parse();
            document = parser.getPDDocument();
            textoPdf = sanitize(new PDFTextStripper().getText(document));
            registrarLog("DEBUG Texto PDF", textoPdf, null, "Boleto", codUsu, idInstPrn);
        } catch (IOException e) {
            registrarLog("Erro PDFBox", e.getMessage(), null, "Boleto", codUsu, idInstPrn);
        } finally {
            if (document != null) try { document.close(); } catch (IOException ignored) {}
        }

        Pattern pBoleto = Pattern.compile(
                "\\b\\d{5}[\\.\\- ]?\\d{5}(?:[\\.\\- ]?\\d{5}[\\.\\- ]?\\d{6}){2}[\\.\\- ]?\\d[\\.\\- ]?\\d{14}\\b"
        );
        codigo = tryPattern(pBoleto, textoPdf);

        if (codigo == null) {
            Pattern pArrecadacao = Pattern.compile(
                    "\\b(?:(?:\\d{11}[\\s\\-]?\\d)[\\s\\-]?){4}\\b"
            );
            codigo = tryPattern(pArrecadacao, textoPdf);
        }

        if (codigo == null) {
            try {
                codigo = BoletoZXingExtractor.decodeBarcodeFromPdf(pdfBytes);
                if (codigo != null) {
                    registrarLog("Sucesso ZXing", "Código ZXing: " + codigo, codigo, "Boleto", codUsu, idInstPrn);
                }
            } catch (Exception e) {
                registrarLog("Erro ZXing", e.getMessage(), null, "Boleto", codUsu, idInstPrn);
            }
        }

        if (codigo == null) {
            try {
                String ocr = sanitize(ApiOcr.capturaCodigoBarras(pdfBytes, codUsu, idInstPrn));
                registrarLog("DEBUG Texto OCR", ocr, null, "Boleto", codUsu, idInstPrn);
                codigo = tryPattern(pBoleto, ocr);
                if (codigo == null) {
                    Pattern pArr2 = Pattern.compile(
                            "\\b(?:(?:\\d{11}[\\s\\-]?\\d)[\\s\\-]?){4}\\b"
                    );
                    codigo = tryPattern(pArr2, ocr);
                }
            } catch (Exception e) {
                registrarLog("Erro API OCR", e.getMessage(), null, "Boleto", codUsu, idInstPrn);
            }
        }

        if (codigo != null && !codigo.isEmpty()) {
            registrarLog("Sucesso Extraído", "Código extraído: " + codigo, codigo, "Boleto", codUsu, idInstPrn);
            updateData(vo, tabela, codigo.replaceAll("\\D+", ""), null);
        } else {
            registrarLog("Falha Extraído", "não foi poss�vel extrair o Código de barras.", null, "Boleto", codUsu, idInstPrn);
            updateData(vo, tabela, null, "Falha na Extraído");
        }
    }

    private static String tryPattern(Pattern p, String txt) {
        Matcher m = p.matcher(txt);
        if (m.find()) {
            return m.group().replaceAll("\\D+", "");
        }
        return null;
    }

    private void updateData(DynamicVO vo, String tabela, String codigo, String erro) throws Exception {
        JapeWrapper dao = JapeFactory.dao(tabela);
        DynamicVO atual = dao.findByPK(vo.asBigDecimal("IDINSTPRN"), BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE);
        FluidUpdateVO upd = dao.prepareToUpdate(atual);
        upd.set("CODBARRAS", codigo);
        upd.set("MSGERRCODBARRAS", erro);
        upd.update();
    }

    @Override public void beforeInsert(PersistenceEvent event) throws Exception {}
    @Override public void beforeUpdate(PersistenceEvent event) throws Exception {}
    @Override public void beforeDelete(PersistenceEvent event) throws Exception {}
    @Override public void afterDelete(PersistenceEvent event) throws Exception {}
    @Override public void beforeCommit(TransactionContext tranCtx) throws Exception {}
}