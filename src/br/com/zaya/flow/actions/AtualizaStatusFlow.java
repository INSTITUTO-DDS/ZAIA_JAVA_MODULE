package br.com.zaya.flow.actions;

import br.com.sankhya.dwf.services.ServiceUtils;
import br.com.sankhya.extensions.actionbutton.AcaoRotinaJava;
import br.com.sankhya.extensions.actionbutton.ContextoAcao;
import br.com.sankhya.extensions.actionbutton.Registro;
import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.core.JapeSession;
import br.com.sankhya.jape.dao.JdbcWrapper;
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
import br.com.sankhya.workflow.model.services.ListaTarefaSP;
import br.com.sankhya.workflow.model.services.ListaTarefaSPHome;
import br.com.sankhya.ws.ServiceContext;
import br.com.sankhya.ws.transformer.json.Json2XMLParser;
import br.com.zaya.flow.events.EventoFinanceiroFlow;
import com.google.gson.JsonObject;
import com.sankhya.util.JsonUtils;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.Collection;
import java.util.Objects;

public class AtualizaStatusFlow implements AcaoRotinaJava {
    private final EntityFacade dwFacade = EntityFacadeFactory.getDWFFacade();
    private br.com.sankhya.workflow.model.services.ListaTarefaSP ListaTarefaSP;
    private JdbcWrapper jdbc = null;

    @Override
    public void doAction(ContextoAcao ctx) throws Exception {

        for (int i = 0; i < ctx.getLinhas().length; i++) {
            Registro reg = ctx.getLinhas()[i];

            jdbc = dwFacade.getJdbcWrapper();
            jdbc.openSession();

            NativeSql qryFin = new NativeSql(jdbc);
            qryFin.setReuseStatements(false);
            qryFin.loadSql(EventoFinanceiroFlow.class, "queFinanceiros.sql");
            qryFin.cleanParameters();
            qryFin.setNamedParameter("NUFIN", reg.getCampo("NUFIN"));

            ResultSet rs = qryFin.executeQuery();

            while (rs.next()) {

                String nomeTarefaFin = reg.getCampo("AD_NOMETAREFA").toString();
                String nomeTarefa = rs.getString("NOMETAREFA");
                if ( !nomeTarefa.equals(nomeTarefaFin) ) {
                    throw new MGEModelException("Esta a��o s� pode ser executada quando a tarefa do Flow for:<br><b>"+nomeTarefa+"</b><br>A tarefa atual �:<br><b>" + nomeTarefaFin + ".</b>");
                }

                BigDecimal nuNota = (BigDecimal) reg.getCampo("NUNOTA");
                BigDecimal nuFin = (BigDecimal) reg.getCampo("NUFIN");
                DynamicVO tnfCabVO = JapeFactory.dao(DynamicEntityNames.CABECALHO_NOTA).findByPK(nuNota);
                BigDecimal idInstPrn = rs.getBigDecimal("IDINSTPRN");
                BigDecimal codPrn = rs.getBigDecimal("CODPRN");
                BigDecimal versao = rs.getBigDecimal("VERSAO");
                String idElemento = rs.getString("IDELEMENTO");
                String status = "AJ";
                String obs = (String) ctx.getParam("OBS");
                String statusComp = (String) ctx.getParam("STATUSANXCOMP");

                Collection<DynamicVO> tarefasVO = JapeFactory.dao("AD_TWFTSF").find("this.VALIDA = 'S' AND this.IDELEMENTO = ?", idElemento);

                if ( tarefasVO.isEmpty() ) {
                    throw new MGEModelException("O processo ao qual este nro. �nico est� vinculado n�o se encontra mais em uma etapa do setor financeiro.<br>A a��o n�o pode ser executada!");
                }

                try {

                    BigDecimal grupoValidacao = JapeFactory.dao(DynamicEntityNames.PROCESSO_NEGOCIO).findByPK(codPrn, versao).asBigDecimal("AD_GRUPOVALIDACAO");
                    String tabelaNotas = Objects.equals(grupoValidacao, BigDecimal.ONE) ? "AD_FATCONNOTAS" : "AD_REQNOTAS";
                    tabelaNotas = codPrn.compareTo(new BigDecimal(12)) == 0 ? "AD_FLXDEPDOC" : tabelaNotas;
                    String situaFin = Objects.equals(grupoValidacao, BigDecimal.ONE) ? "S" : statusComp;
                    String msg = Objects.equals(grupoValidacao, BigDecimal.ONE) && statusComp.contains("AP") ? "Esta op��o n�o pode ser usada com este processo. Favor selecionar outro tipo de ajuste." : "";

                    if(!msg.isEmpty()){
                        throw new MGEModelException(msg);
                    }

                    JapeWrapper aprovacaoDAO = JapeFactory.dao("AD_APROVACAO");
                    Collection<DynamicVO> aprovacoesVO = aprovacaoDAO.find("this.IDINSTPRN = ?", idInstPrn);

                    JapeWrapper fatConNotasDAO = JapeFactory.dao(tabelaNotas);
                    Collection<DynamicVO> fatConNotasVO = fatConNotasDAO.find("this.IDINSTPRN = ?", idInstPrn);

                    for (DynamicVO aprovacaoVO : aprovacoesVO) {
                        FluidUpdateVO updAprovacaoVO = aprovacaoDAO.prepareToUpdate(aprovacaoVO);

                        updAprovacaoVO.set("SITUAFIN", situaFin);
                        updAprovacaoVO.set("OBSFIN", obs);
                        updAprovacaoVO.set("STATUSANXCOMP", statusComp);
                        updAprovacaoVO.update();

                        reg.setCampo("AD_FATOBS", obs);
                        reg.setCampo("AD_FATRSTATUS", statusComp);
                        reg.setCampo("AD_FATRSTATUSPAG", status);
                    }

                    for (DynamicVO fatConNotaVO : fatConNotasVO) {
                        FluidUpdateVO updFatConNotas = fatConNotasDAO.prepareToUpdate(fatConNotaVO);
                        updFatConNotas.set("STATUS", status);
                        updFatConNotas.update();
                    }

                    encTarefa(nuFin);

                    for (DynamicVO apvVO : aprovacoesVO) {
                        FluidUpdateVO updApVO = aprovacaoDAO.prepareToUpdate(apvVO);
                        updApVO.set("SITUAFIN", null);
                        updApVO.set("STATUSANXCOMP", null);
                        updApVO.update();

                    }

                    ctx.setMensagemRetorno("Dados atualizados! ");

                } catch (Exception e) {
                    e.printStackTrace();
                    throw new MGEModelException(e.getMessage());
                }
            }
        }
    }

    public void encTarefa(BigDecimal nuFin) throws MGEModelException {
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
            throw new MGEModelException(e.getMessage());
        } finally {
            jdbc.closeSession();
        }
    }
}
