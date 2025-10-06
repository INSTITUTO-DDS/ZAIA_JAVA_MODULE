package br.com.zaya.flow.helper;

import br.com.sankhya.dwf.services.ServiceUtils;
import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.core.JapeSession;
import br.com.sankhya.jape.dao.JdbcWrapper;
import br.com.sankhya.jape.event.ModifingFields;
import br.com.sankhya.jape.sql.NativeSql;
import br.com.sankhya.jape.util.JapeSessionContext;
import br.com.sankhya.jape.vo.DynamicVO;
import br.com.sankhya.jape.wrapper.JapeFactory;
import br.com.sankhya.jape.wrapper.JapeWrapper;
import br.com.sankhya.jape.wrapper.fluid.FluidUpdateVO;
import br.com.sankhya.modelcore.MGEModelException;
import br.com.sankhya.modelcore.auth.AuthenticationInfo;
import br.com.sankhya.modelcore.util.DynamicEntityNames;
import br.com.sankhya.modelcore.util.EntityFacadeFactory;
import br.com.sankhya.modelcore.util.SWRepositoryUtils;
import br.com.sankhya.module.annotation.Jape;
import br.com.sankhya.workflow.model.services.ListaTarefaSP;
import br.com.sankhya.workflow.model.services.ListaTarefaSPHome;
import br.com.sankhya.ws.ServiceContext;
import br.com.sankhya.ws.transformer.json.Json2XMLParser;
import br.com.zaya.flow.actions.AtualizaComprovanteFlow;
import br.com.zaya.flow.actions.AtualizaStatusFlow;
import br.com.zaya.flow.actions.CampoAnexoBlob;
import br.com.zaya.flow.events.EventoFinanceiroFlow;
import com.google.gson.JsonObject;
import com.sankhya.util.BigDecimalUtil;
import com.sankhya.util.JsonUtils;
import com.sankhya.util.SessionFile;
import com.sankhya.util.StringUtils;

import java.io.File;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Collection;
import java.util.Objects;

public class FlowHelperIdds {
    private static final EntityFacade dwFacade = EntityFacadeFactory.getDWFFacade();
    private static JdbcWrapper jdbc = dwFacade.getJdbcWrapper();
    private JapeWrapper finDAO = JapeFactory.dao(DynamicEntityNames.FINANCEIRO);
    private FluidUpdateVO finUpdVO;

    public void finalizaTarefaBaixa(ModifingFields mdf, DynamicVO dynamicVO) throws MGEModelException {
        try {
            System.out.println("___Iniciando FinalizaTarefaBaixa___");
            jdbc = dwFacade.getJdbcWrapper();
            jdbc.openSession();

            NativeSql qryFin = new NativeSql(jdbc);
            qryFin.setReuseStatements(false);
            qryFin.loadSql(EventoFinanceiroFlow.class, "queFinanceiros.sql");
            qryFin.cleanParameters();
            qryFin.setNamedParameter("NUFIN", dynamicVO.asBigDecimal("NUFIN"));

            ResultSet rs = qryFin.executeQuery();

            while (rs.next()) {
                System.out.println("___Entrei no while dos financeiros___");
                if ( mdf.isModifing("DHBAIXA") && mdf.getNewValue("DHBAIXA") != null ) {
                    JapeSession.SessionHandle hnd = null;
                    ResultSet rsDados = null;
                    System.out.println("___Título sendo baixado___");

                    try {

                        Timestamp dhBaixa = dynamicVO.asTimestamp("DHBAIXA");
                        BigDecimal nuFin = dynamicVO.asBigDecimal("NUFIN");
                        BigDecimal vlrBaixa = dynamicVO.asBigDecimal("VLRBAIXA");
                        BigDecimal codBco = dynamicVO.asBigDecimal("CODBCO");
                        String companx = dynamicVO.asString("AD_COMPANX");
                        String compAnxo = (companx == null || companx.isEmpty()) ? "N" : companx;
                        BigDecimal idInstPrn = rs.getBigDecimal("IDINSTPRN");
                        String status = "PI";
                        String obs = "Aprovado (Finalização automática de tarefa por baixa do financeiro).";
                        String nomeTarefaFin = dynamicVO.asString("AD_NOMETAREFA");
                        String autenticacao = dynamicVO.asString("AD_AUTENTICACAO");
                        String idElemento;

                        hnd = JapeSession.open();

                        idElemento = rs.getString("IDELEMENTO");

                        System.out.println("Codigo do banco vindo do banco: " + codBco);

                        AuthenticationInfo auth = new AuthenticationInfo("SUP", BigDecimal.ZERO, BigDecimal.ZERO, 0);
                        auth.makeCurrent();
                        ServiceContext sctx = new ServiceContext(null);
                        sctx.setAutentication(auth);
                        sctx.makeCurrent();
                        JapeSessionContext.putProperty("usuario_logado", auth.getUserID());
                        JapeSession.putProperty("usuario_logado", auth.getUserID());

                        BigDecimal nuRfe = null;
                        BigDecimal codUsu = auth.getUserID();

                        NativeSql qryRfe = new NativeSql(jdbc);
                        qryRfe.setReuseStatements(false);
                        qryRfe.loadSql(FlowHelperIdds.class, "queRelatorio.sql");
                        qryRfe.setNamedParameter("NUFIN", nuFin);

                        ResultSet rsRfe = null;
                        rsRfe = qryRfe.executeQuery();

                        compAnxo = autenticacao != null ? "N" : compAnxo;

                        if( !rsRfe.isBeforeFirst() && compAnxo.equals("N")) {
                            throw new Exception("<b>Não é possível realizar a baixa deste financeiro!</b><br>" +
                                    "Este título é referente ao processo de nro.: "+idInstPrn+" do Flow. " +
                                    "Para baixar o financeiro adicione um comprovante manualmente ou então verifique se existe comprovante personalizado para este tipo de título.");
                        }
                        while (rsRfe.next()) {
                            nuRfe = rsRfe.getBigDecimal("CODRELATORIO");
                            System.out.println("Codigo do Comprovante: " + nuRfe);
                        }

                        System.out.println("Dados vindo do banco: ");
                        System.out.println("Nufin: " + nuFin);
                        System.out.println("idElemento: " + idElemento);
                        System.out.println("vlrBaixa: " + vlrBaixa);
                        System.out.println("nuRfe: " + nuRfe);
                        System.out.println("codUsu: " + codUsu);
                        System.out.println("dhBaixa: " + dhBaixa);

                        Collection<DynamicVO> tarefasVO = JapeFactory.dao("AD_TWFTSF")
                                .find("this.VALIDA = 'S' AND this.IDELEMENTO = ?", idElemento);

                        System.out.println("Conteúdo vindo da tabela de tarefas: " + tarefasVO.isEmpty());
                        if ( tarefasVO.isEmpty() ) {
                            System.out.println(
                                    "O processo ao qual este nro. único está vinculado não se encontra mais em uma etapa do setor financeiro.<br>A ação não pode ser executada!");
                            return;
                        }

                        if ( dhBaixa != null ) {
                            if ( !BigDecimalUtil.isNullOrZero(vlrBaixa) ) {

                                if(compAnxo.equals("S")) {
                                    String arquivo = StringUtils.getValueOrDefault(getFilePath(nuFin), "N");
                                    System.out.println("Entrei aqui: Tem arquivo? "+arquivo);
                                    if(arquivo.contains("N")) {
                                        throw new Exception("<b>Não foi possível realizar baixa!</b><br>" +
                                                "Este título indica que o comprovante foi anexado manualmente, porém nenhum arquivo em anexo foi encontrado.");
                                    }
                                }

                                if(compAnxo.equals("N")) {
                                    if(nuRfe == null) {
                                        throw new MGEModelException("<b>Não foi possível realizar baixa!</b><br>" +
                                                "Ocorreu um erro ao tentar gerar o comprovante personalizado.");
                                    }else {
                                        System.out.println("Esta no if para chamar método de Comprovante");
                                        GerenciadorArquivos arquivo = new GerenciadorArquivos();
                                        arquivo.obtemRelatorio(nuFin, nuRfe, codUsu);
                                    }
                                }

                                try {
                                    String tabelaNotas = "AD_FATCONNOTAS";

                                    JapeWrapper adFatconnotasDAO = JapeFactory.dao(tabelaNotas);
                                    Collection<DynamicVO> fatConNotasVO = adFatconnotasDAO.find("this.IDINSTPRN = ?", idInstPrn);

                                    JapeWrapper aprovacaoDAO = JapeFactory.dao("AD_APROVACAO");
                                    Collection<DynamicVO> aprovacoesVO = aprovacaoDAO.find("this.IDINSTPRN = ?", idInstPrn);

                                    for (DynamicVO fatConNotaVO : fatConNotasVO) {
                                        FluidUpdateVO updFatConNotasVO = adFatconnotasDAO.prepareToUpdate(fatConNotaVO);
                                        updFatConNotasVO.set("STATUS", status);
                                        updFatConNotasVO.update();

                                        finUpdVO = finDAO.prepareToUpdate(dynamicVO);
                                        finUpdVO.set("AD_FATRSTATUSPAG", status);
                                        finUpdVO.update();

                                    }

                                    for (DynamicVO aprovacaoVO : aprovacoesVO) {
                                        FluidUpdateVO updAprovacaoVO = aprovacaoDAO.prepareToUpdate(aprovacaoVO);
                                        updAprovacaoVO.set("SITUAFIN", "S");
                                        updAprovacaoVO.set("OBSFIN", obs);
                                        updAprovacaoVO.set("STATUSANXCOMP", null);
                                        updAprovacaoVO.update();
                                    }

                                    File file = new File(getFilePath(nuFin));
                                    byte[] pdfBytes = Files.readAllBytes(file.toPath());

                                    if ( pdfBytes != null && pdfBytes.length > 0 ) {
                                        try {
                                            ServiceContext contexto = ServiceContext.getCurrent();
                                            SessionFile sessionFile = SessionFile.createSessionFile("comprovante.pdf", "application/pdf", pdfBytes);

                                            contexto.putHttpSessionAttribute("sessionkey", sessionFile);

                                            updateData(pdfBytes, idInstPrn, tabelaNotas);
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                            throw new MGEModelException("Erro:" + e.getMessage());
                                        }
                                    }

                                    try {
                                        encTarefa(nuFin);
                                    }finally {
                                        System.out.println("___Atualizando dados no financeiro FinalizaTarefaBaixa___");
                                        try {

                                            jdbc = dwFacade.getJdbcWrapper();
                                            jdbc.openSession();

                                            NativeSql qryCodBarras = new NativeSql(jdbc);
                                            qryCodBarras.setReuseStatements(false);
                                            qryCodBarras.loadSql(FlowHelperIdds.class, "queAtualDadosFinanceiros.sql");
                                            qryCodBarras.setNamedParameter("NUFIN", dynamicVO.asBigDecimal("NUFIN"));

                                            ResultSet rsCodBarras = qryCodBarras.executeQuery();
                                            while (rsCodBarras.next()) {
                                                JapeWrapper finDAO = JapeFactory.dao(DynamicEntityNames.FINANCEIRO);
                                                FluidUpdateVO finUpdVO = finDAO.prepareToUpdate(dynamicVO);
                                                finUpdVO.set("AD_IDELEMENTO", rsCodBarras.getString("IDELEMENTO"));
                                                finUpdVO.set("AD_NOMETAREFA", rsCodBarras.getString("NOMETAREFA"));
                                                finUpdVO.set("AD_FATOBSSOLICIT", rsCodBarras.getString("AJUSTESTEXT"));
                                                finUpdVO.update();
                                            }
                                            rsCodBarras.close();
                                        }catch (Exception e){
                                            e.printStackTrace();
                                        }finally {
                                            jdbc.closeSession();
                                        }
                                    }
                                } catch (Exception e) {
                                    e.printStackTrace();
                                    throw new MGEModelException(e.getMessage());
                                }
                            }
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                        throw new MGEModelException("Erro durante a execução do evento que realiza a baixa do título no Flow: " + e.getMessage());
                    } finally {
                        JapeSession.close(hnd);
                    }
                } else {
                    System.err.println("Não é baixa.");
                }
            }
            rs.close();
        } catch (Exception e) {
            e.printStackTrace();
            throw new MGEModelException(e);
        }finally {
            jdbc.closeSession();
        }
    }

    public void finalizarTarefaRemessa(ModifingFields mdf, DynamicVO dynamicVO) throws MGEModelException {
        if ( mdf.isModifing("NUMREMESSA") && mdf.getNewValue("NUMREMESSA") != null ) {
            String nomeTarefa = "Analisar Lançamento - Financeiro";
            JapeSession.SessionHandle hnd = null;
            ResultSet rsDados = null;

            try {
                BigDecimal nuFin = dynamicVO.asBigDecimal("NUFIN");
                BigDecimal nuNota = dynamicVO.asBigDecimal("NUNOTA");
                DynamicVO tnfCabVO = JapeFactory.dao(DynamicEntityNames.CABECALHO_NOTA).findByPK(nuNota);
                BigDecimal idInstPrn = tnfCabVO.asBigDecimal("AD_IDINSTPRN");
                String status = "S";
                String obs = "Pagamento autorizado pelo setor financeiro. Remessa de pagamento de nº"+mdf.getNewValue("NUMREMESSA")+" gerada e enviada ao banco.";

                hnd = JapeSession.open();
                jdbc = dwFacade.getJdbcWrapper();
                jdbc.openSession();

                NativeSql qryDados = new NativeSql(jdbc);
                qryDados.setReuseStatements(false);
                qryDados.loadSql(FlowHelperIdds.class, "arquivo.sql");
                qryDados.setNamedParameter("NUFIN", nuFin);

                rsDados = qryDados.executeQuery();
                String idElemento = " ";

                while (rsDados.next()) {
                    nomeTarefa = rsDados.getString("NOMETAREFA");
                    idElemento = rsDados.getString("IDELEMENTO");
                }

                if ( !nomeTarefa.equals(nomeTarefa) ) {
                    System.out.println(
                            "Esta ação só pode ser executada quando a tarefa do Flow for:<br><b>Analisar Lançamento - Financeiro.</b><br>A tarefa atual é:<br><b>"
                                    + dynamicVO.asString("AD_NOMETAREFA") + ".</b>");
                    return;
                }

                AuthenticationInfo auth = new AuthenticationInfo("SUP", BigDecimal.ZERO, BigDecimal.ZERO, 0);
                auth.makeCurrent();
                ServiceContext sctx = new ServiceContext(null);
                sctx.setAutentication(auth);
                sctx.makeCurrent();
                JapeSessionContext.putProperty("usuario_logado", auth.getUserID());
                JapeSession.putProperty("usuario_logado", auth.getUserID());
                BigDecimal codUsu = auth.getUserID();

                Collection<DynamicVO> tarefasVO = JapeFactory.dao("AD_TWFTSF")
                        .find("this.VALIDA = 'S' AND this.IDELEMENTO = ?", idElemento);

                if ( tarefasVO.isEmpty() ) {
                    System.out.println(
                            "O processo ao qual este nro. único está vinculado não se encontra mais em uma etapa do setor financeiro.<br>A ação não pode ser executada!");
                    return;
                }

                try {

                    JapeWrapper aprovacaoDAO = JapeFactory.dao("AD_APROVACAO");
                    Collection<DynamicVO> aprovacoesVO = aprovacaoDAO.find("this.IDINSTPRN = ?", idInstPrn);

                    for (DynamicVO aprovacaoVO : aprovacoesVO) {
                        FluidUpdateVO updAprovacaoVO = aprovacaoDAO.prepareToUpdate(aprovacaoVO);
                        updAprovacaoVO.set("SITUAFIN", status);
                        updAprovacaoVO.set("OBSFIN", obs);
                        updAprovacaoVO.update();

                        finUpdVO = finDAO.prepareToUpdate(dynamicVO);
                        finUpdVO.set("AD_FATOBS", obs);
                        finUpdVO.set("AD_FATRSTATUS", status);
                        finUpdVO.update();
                    }

                    encTarefa(nuFin);

                } catch (Exception e) {
                    e.printStackTrace();
                    throw new MGEModelException(e.getMessage());
                }


            } catch (Exception e) {
                throw new MGEModelException("Erro durante a execução do evento que realiza a baixa do título no Flow: " + e.getMessage());
            } finally {
                JapeSession.close(hnd);
            }
        } else {
            System.err.println("Não é remessa.");
        }
    }

    public void encTarefa(BigDecimal nuFin) throws MGEModelException {
        System.out.println("Iniciando encTarefa com nuFin: " + nuFin);
        try {
            ListaTarefaSP listaTarefaSP = (ListaTarefaSP) ServiceUtils.getStatelessFacade(ListaTarefaSPHome.JNDI_NAME, ListaTarefaSPHome.class);
            jdbc = dwFacade.getJdbcWrapper();
            jdbc.openSession();

            BigDecimal processId = null;
            BigDecimal processInstanceId = null;
            BigDecimal taskInstanceId = null;
            String taskIdElemento = "";

            NativeSql qryTarefas = new NativeSql(jdbc);

            qryTarefas.setReuseStatements(false);
            qryTarefas.loadSql(AtualizaStatusFlow.class, "queDadosTarefa.sql");
            qryTarefas.cleanParameters();
            qryTarefas.setNamedParameter("NUFIN", nuFin);

            ResultSet rsTarefas = null;
            rsTarefas = qryTarefas.executeQuery();

            while (rsTarefas.next()) {
                processId = rsTarefas.getBigDecimal("PROCESSID");
                processInstanceId = rsTarefas.getBigDecimal("PROCESSINSTANCEID");
                taskInstanceId = rsTarefas.getBigDecimal("TASKINSTANCEID");
                taskIdElemento = rsTarefas.getString("TASKIDELEMENTO");
            }
            rsTarefas.close();

            AuthenticationInfo auth = new AuthenticationInfo("SUP", BigDecimal.ZERO, BigDecimal.ZERO, 0);
            auth.makeCurrent();
            ServiceContext sctx = new ServiceContext(null);
            sctx.setAutentication(auth);
            sctx.makeCurrent();
            JapeSessionContext.putProperty("usuario_logado", auth.getUserID());
            JapeSession.putProperty("usuario_logado", auth.getUserID());

            String params = String.format(
                    "{\n" +
                            "    \"param\": {\n" +
                            "        \"processId\": \"" + processId + "\",\n" +
                            "        \"processInstanceId\": " + processInstanceId + ",\n" +
                            "        \"taskIdElemento\": " + taskIdElemento + ",\n" +
                            "        \"taskInstanceId\": \"" + taskInstanceId + "\"\n" +
                            "    }\n" +
                            "}"
            );

            String params2 = String.format(
                    "{\n" +
                            "    \"param\": {\n" +
                            "        \"processInstanceId\": " + processInstanceId + ",\n" +
                            "        \"taskInstanceId\": \"" + taskInstanceId + "\"\n" +
                            "    }\n" +
                            "}"
            );

            JsonObject param = JsonUtils.convertStringToJsonObject(params2);
            sctx.setJsonRequestBody(param);
            sctx.setRequestBody(Json2XMLParser.jsonToElement("param", sctx.getJsonRequestBody()));
            listaTarefaSP.verificaTarefaExcluidaOuCancelada(sctx);
            sctx.getJsonRequestBody().remove("param");

            param = JsonUtils.convertStringToJsonObject(params);
            sctx.setJsonRequestBody(param);
            sctx.setRequestBody(Json2XMLParser.jsonToElement("param", sctx.getJsonRequestBody()));
            listaTarefaSP.finishTask(sctx);

        } catch (Exception e) {
            e.printStackTrace();
            throw new MGEModelException(e.getMessage());
        } finally {
            jdbc.closeSession();
        }
    }

    private void updateData(byte[] pdfBytes, BigDecimal idInstPrn, String tabela) throws Exception {
        JapeWrapper fatConNotasDAO = JapeFactory.dao(tabela);
        DynamicVO fatConNotasVO = fatConNotasDAO.findByPK(idInstPrn, new BigDecimal(0), new BigDecimal(1), new BigDecimal(1));


        System.out.println("Antes de entrar no metodo de conversão");
        CampoAnexoBlob anexoBlob = new CampoAnexoBlob(pdfBytes, "Comprovante", 135005, "application/octet-stream", "Jun 10, 2024 10:56:02");

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

            System.out.println(filePath);

            return filePath;
        } catch (Exception e) {
            throw new Exception("Erro em obter o File Path " + e.getMessage());
        }
    }
}
