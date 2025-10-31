package br.com.zaya.flow.actions;

import br.com.sankhya.extensions.actionbutton.AcaoRotinaJava;
import br.com.sankhya.extensions.actionbutton.ContextoAcao;
import br.com.sankhya.extensions.actionbutton.Registro;
import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.dao.JdbcWrapper;
import br.com.sankhya.jape.sql.NativeSql;
import br.com.sankhya.jape.vo.DynamicVO;
import br.com.sankhya.jape.wrapper.JapeFactory;
import br.com.sankhya.jape.wrapper.JapeWrapper;
import br.com.sankhya.jape.wrapper.fluid.FluidUpdateVO;
import br.com.sankhya.modelcore.MGEModelException;
import br.com.sankhya.modelcore.util.DynamicEntityNames;
import br.com.sankhya.modelcore.util.EntityFacadeFactory;
import br.com.sankhya.modelcore.util.SWRepositoryUtils;
import br.com.sankhya.ws.ServiceContext;
import com.sankhya.util.SessionFile;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.sql.ResultSet;
import java.util.Collection;

public class AtualizaComprovanteFlow implements AcaoRotinaJava {
    private final static EntityFacade dwFacade = EntityFacadeFactory.getDWFFacade();
    private final static JdbcWrapper jdbc = dwFacade.getJdbcWrapper();
    @Override
    public void doAction(ContextoAcao ctx) throws Exception {
        for(int i = 0; i < ctx.getLinhas().length; i++){
            Registro reg = ctx.getLinhas()[i];

            String nomeTarefa = "Anexar Comprovante de Pagamento";
            if(!nomeTarefa.equals(reg.getCampo("AD_NOMETAREFA"))){
                throw new MGEModelException("Esta ação só pode ser executada quando a tarefa do Flow for:<br><b>Anexar Comprovante de Pagamento.</b><br>A tarefa atual �:<br><b>"+reg.getCampo("AD_NOMETAREFA")+".</b>");
            }

            BigDecimal nuNota = (BigDecimal) reg.getCampo("NUNOTA");
            BigDecimal nuFin = (BigDecimal) reg.getCampo("NUFIN");
            DynamicVO tnfCabVO = JapeFactory.dao(DynamicEntityNames.CABECALHO_NOTA).findByPK(nuNota);
            BigDecimal idInstPrn = tnfCabVO.asBigDecimal("AD_IDINSTPRN");
            String status = (String) ctx.getParam("STATUS");
            String idElemento = (String) reg.getCampo("AD_IDELEMENTO");
            File file = new File(getFilePath(nuFin));
            System.out.println("Entrei aqui:"+getFilePath(nuFin));
            byte[] pdfBytes = Files.readAllBytes(file.toPath());

            Collection<DynamicVO> tarefasVO = JapeFactory.dao("AD_TWFTSF").find("this.VALIDA = 'S' AND this.IDELEMENTO = ?", idElemento);

            if( tarefasVO.isEmpty() ){
                throw new MGEModelException("O processo ao qual este nro. Único está vinculado não se encontra mais em uma etapa do setor financeiro.<br>A ação não pode ser executada!");
            }

            try{

                JapeWrapper aprovacaoDAO = JapeFactory.dao("AD_FATCONNOTAS");
                Collection<DynamicVO> aprovacoesVO = aprovacaoDAO.find("this.IDINSTPRN = ?", idInstPrn);

                for(DynamicVO aprovacaoVO : aprovacoesVO){
                    FluidUpdateVO updAprovacaoVO = aprovacaoDAO.prepareToUpdate(aprovacaoVO);
                    updAprovacaoVO.set("STATUS", status);
                    updAprovacaoVO.update();

                    reg.setCampo("AD_FATRSTATUSPAG", status);

                }

                AtualizaStatusFlow atualizaStatusFlow = new AtualizaStatusFlow();
                atualizaStatusFlow.encTarefa(nuFin);

                if (pdfBytes != null && pdfBytes.length > 0) {
                    try {
                        ServiceContext contexto = ServiceContext.getCurrent();
                        SessionFile sessionFile = SessionFile.createSessionFile("comprovante.pdf", "application/pdf", pdfBytes);

                        contexto.putHttpSessionAttribute("sessionkey", sessionFile);

                        updateData(pdfBytes, idInstPrn);
                    } catch (Exception e) {
                        e.printStackTrace();
                        ctx.mostraErro("Erro:" + e.getMessage());
                    }
                }

                ctx.setMensagemRetorno("Dados atualizados!");

            }catch (Exception e){
                e.printStackTrace();
                throw new MGEModelException(e.getMessage());
            }
        }
    }

    private void updateData(byte[] pdfBytes, BigDecimal idInstPrn) throws Exception {
        JapeWrapper fatConNotasDAO = JapeFactory.dao("AD_FATCONNOTAS");
        DynamicVO fatConNotasVO = fatConNotasDAO.findByPK(idInstPrn, new BigDecimal(0), new BigDecimal(1), new BigDecimal(1));


        System.out.println("Antes de entrar no metodo de conversão");
        CampoAnexoBlob anexoBlob = new CampoAnexoBlob(pdfBytes, "Comprovante", 135005, "application/octet-stream", "Jun 10, 2024 10:56:02");

        // Ajusta a atualiza��o de dados usando FluidUpdateVO
        FluidUpdateVO fUpdateVO = fatConNotasDAO.prepareToUpdate(fatConNotasVO);
        fUpdateVO.set("COMPPAG", anexoBlob.getArquivo());
        fUpdateVO.update();
    }

    private String getFilePath(BigDecimal nuFin) throws Exception {

        try {
            NativeSql qryArquivo = new NativeSql(jdbc);
            qryArquivo.setReuseStatements(false);
            qryArquivo.loadSql(AtualizaComprovanteFlow.class, "queDadosAnexo.sql");
            qryArquivo.cleanParameters();
            qryArquivo.setNamedParameter("NUFIN", nuFin);
            ResultSet rsArquivo = qryArquivo.executeQuery();

            String chave = "";
            String filePath = "";

            while (rsArquivo.next()) {
                chave = rsArquivo.getString("CHAVEARQUIVO");
                filePath = SWRepositoryUtils.getBaseFolder() + "/Sistema/Anexos/Financeiro/" + chave;
            }
            rsArquivo.close();
            jdbc.closeSession();

            System.out.println(filePath);

            return filePath;
        } catch (Exception e) {
            throw new Exception("Erro em obter o File Path " + e.getMessage());
        }
    }
}
