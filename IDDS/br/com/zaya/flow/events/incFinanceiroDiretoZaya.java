package br.com.zaya.flow.events;

import br.com.sankhya.extensions.actionbutton.Registro;
import br.com.sankhya.extensions.flow.ContextoTarefa;
import br.com.sankhya.extensions.flow.TarefaJava;
import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.dao.JdbcWrapper;
import br.com.sankhya.jape.sql.NativeSql;
import br.com.sankhya.jape.vo.DynamicVO;
import br.com.sankhya.jape.vo.EntityVO;
import br.com.sankhya.jape.wrapper.JapeFactory;
import br.com.sankhya.jape.wrapper.JapeWrapper;
import br.com.sankhya.jape.wrapper.fluid.FluidUpdateVO;
import br.com.sankhya.modelcore.MGEModelException;
import br.com.sankhya.modelcore.util.DynamicEntityNames;
import br.com.sankhya.modelcore.util.EntityFacadeFactory;
import br.com.zaya.flow.helper.FlowHelperIdds;
import com.sankhya.util.StringUtils;
import com.sankhya.util.TimeUtils;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.Optional;

public class incFinanceiroDiretoZaya implements TarefaJava {
    @Override
    public void executar(ContextoTarefa contexto) throws Exception {
        this.executarInterno(contexto);
    }

    public void executarInterno(ContextoTarefa contexto) throws Exception {
        EntityFacade dwfFacade = EntityFacadeFactory.getDWFFacade();
        BigDecimal usuarioInclusao = NativeSql.getBigDecimal(
                "CODUSUINC", "TWFIPRN", "IDINSTPRN = " + contexto.getIdInstanceProcesso());
        String nomeUsu = NativeSql.getString(
                "NOMEUSU", "TSIUSU", "CODUSU = ?", new Object[]{usuarioInclusao});
        Registro[] fatCon = contexto.getLinhasFormulario("AD_FATCON");
        Registro[] conNotas = contexto.getLinhasFormulario("AD_FATCONNOTAS");

        if (conNotas != null) {
            String observacoes = (String) fatCon[0].getCampo("OBSERVACAO");
            BigDecimal codEmp = (BigDecimal) fatCon[0].getCampo("CODEMP");
            BigDecimal codCen = (BigDecimal) fatCon[0].getCampo("CODCENCUS");
            BigDecimal codParc = (BigDecimal) fatCon[0].getCampo("CODPARC");
            BigDecimal codNat = (BigDecimal) fatCon[0].getCampo("CODNAT");
            BigDecimal vlrNota = conNotas[0].getCampo("VLRNOTA") != null
                    ? (BigDecimal) conNotas[0].getCampo("VLRNOTA")
                    : BigDecimal.ZERO;
            Timestamp dtVenc = (Timestamp) conNotas[0].getCampo("DTVENC");
            BigDecimal codBco = (BigDecimal) conNotas[0].getCampo("CODBCO");
            String codBarra = (String) conNotas[0].getCampo("CODBARRAS");
            String numNota = (String) conNotas[0].getCampo("NUMNOTA");
            System.out.println("incFinanceiroDiretoZaya - CodBarras...: " + codBarra);

            BigDecimal codCtb = NativeSql.getBigDecimal(
                    "CT.CODCTABCOINT",
                    "AD_CRTSICTA C1 INNER JOIN TSICTA CT ON C1.CODCTABCOINT = CT.CODCTABCOINT",
                    "CT.CLASSE = 'C' AND C1.CODCENCUS = ? AND CT.ATIVA = 'S' AND C1.CODEMP = ?",
                    new Object[]{codCen, codEmp});
            if (codCtb == null) {
                codCtb = BigDecimal.ZERO;
            }

            if (vlrNota.compareTo(BigDecimal.ZERO) != 0) {
                observacoes = "### FLUXO DE NOTAS/FATURAS RECORRENTES ### \n \nID solicitação: "
                        + contexto.getIdInstanceProcesso()
                        + " \nSolicitante: " + usuarioInclusao + " - " + nomeUsu
                        + "\nObservação: " + observacoes;
                if (!StringUtils.isEmpty(observacoes) && observacoes.length() > 255) {
                    observacoes = observacoes.substring(0, 250).concat("...");
                }

                String tipoPag = (String) conNotas[0].getCampo("TIPOPAG");
                BigDecimal codTipTit = null;
                if ("B".equals(tipoPag)) codTipTit = BigDecimal.valueOf(20L);
                if ("P".equals(tipoPag)) codTipTit = BigDecimal.valueOf(38L);
                if ("T".equals(tipoPag)) codTipTit = BigDecimal.valueOf(19L);

                BigDecimal nufinRec;
                try {
                    DynamicVO finVo = (DynamicVO) dwfFacade.getDefaultValueObjectInstance("Financeiro");
                    finVo.setProperty("VLRDESDOB", vlrNota);
                    finVo.setProperty("CODBCO", codBco);
                    finVo.setProperty("CODCTABCOINT", codCtb);
                    finVo.setProperty("CODEMP", codEmp);
                    finVo.setProperty("CODNAT", codNat);
                    finVo.setProperty("CODCENCUS", codCen);
                    finVo.setProperty("CODTIPTIT", codTipTit);
                    finVo.setProperty("CODTIPOPER", BigDecimal.valueOf(4100L));
                    finVo.setProperty("DTNEG", TimeUtils.getNow());
                    finVo.setProperty("DTVENC", dtVenc);
                    finVo.setProperty("CODPARC", codParc);
                    finVo.setProperty("RECDESP", BigDecimal.valueOf(-1));

                    try {
                        finVo.setProperty("NUMNOTA", new BigDecimal(numNota));
                    } catch (NumberFormatException e) {
                        throw new MGEModelException("Número da nota inválido: " + numNota +". Este campo aceita somente números!", e);
                    }

                    finVo.setProperty("HISTORICO", observacoes);
                    finVo.setProperty("AD_ORIGEMFLOW", "S");

                    if (codBarra == null || codBarra.trim().isEmpty()) {
                        throw new MGEModelException("Código de barras inválido: " + codBarra);
                    }
                    finVo.setProperty("CODBARRA", codBarra);
                    finVo.setProperty("AD_COMPANX", "S");

                    dwfFacade.createEntity("Financeiro", (EntityVO) finVo);
                    nufinRec = finVo.asBigDecimal("NUFIN");
                } catch (MGEModelException me) {
                    throw me;
                } catch (Exception e) {
                    throw new MGEModelException("Erro ao criar financeiro: " + e.getMessage(), e);
                }

                JdbcWrapper jdbc = EntityFacadeFactory.getDWFFacade().getJdbcWrapper();
                jdbc.openSession();
                NativeSql nativeSql = new NativeSql(jdbc);
                nativeSql.appendSql("INSERT INTO TGFFIN_TNF (TNF_IDINSTTAR, TNF_ESCOPO, TNF_IDINSTPRN, TNF_DHCRIACAO, TNF_IDTAREFA, NUFIN) VALUES (0, 'P', :IDINSTPRN, GETDATE(), 0, :NUFIN)");
                nativeSql.setNamedParameter("IDINSTPRN", contexto.getIdInstanceProcesso());
                nativeSql.setNamedParameter("NUFIN", nufinRec);
                nativeSql.executeUpdate();

                JapeWrapper finDao = JapeFactory.dao(DynamicEntityNames.FINANCEIRO);
                DynamicVO finVO = finDao.findByPK(nufinRec);

                NativeSql qryDados = new NativeSql(jdbc);
                qryDados.setReuseStatements(false);
                qryDados.loadSql(FlowHelperIdds.class, "queDadosFinanceiros.sql");
                qryDados.setNamedParameter("NUFIN", nufinRec);

                ResultSet rsDados = qryDados.executeQuery();
                while (rsDados.next()) {
                    JapeWrapper finDAO = JapeFactory.dao(DynamicEntityNames.FINANCEIRO);
                    FluidUpdateVO finUpdVO = finDAO.prepareToUpdate(finVO);
                    finUpdVO.set("AD_IDELEMENTO", rsDados.getString("IDELEMENTO"));
                    finUpdVO.set("AD_IDINSTPRN", contexto.getIdInstanceProcesso());
                    finUpdVO.set("AD_NOMETAREFA", rsDados.getString("NOMETAREFA"));
                    finUpdVO.set("AD_FATOBSSOLICIT", rsDados.getBigDecimal("AJUSTESTEXT"));
                    finUpdVO.update();
                }
                rsDados.close();

                jdbc.closeSession();
            }
        }
    }
}