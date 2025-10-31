package br.com.zaya.flow.events;


import br.com.sankhya.extensions.eventoprogramavel.EventoProgramavelJava;
import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.core.JapeSession;
import br.com.sankhya.jape.dao.JdbcWrapper;
import br.com.sankhya.jape.event.ModifingFields;
import br.com.sankhya.jape.event.PersistenceEvent;
import br.com.sankhya.jape.event.TransactionContext;
import br.com.sankhya.jape.sql.NativeSql;
import br.com.sankhya.jape.vo.DynamicVO;
import br.com.sankhya.jape.wrapper.JapeFactory;
import br.com.sankhya.jape.wrapper.JapeWrapper;
import br.com.sankhya.jape.wrapper.fluid.FluidUpdateVO;
import br.com.sankhya.modelcore.util.DynamicEntityNames;
import br.com.sankhya.modelcore.util.EntityFacadeFactory;
import br.com.zaya.flow.helper.FlowHelperIdds;

import java.sql.ResultSet;

public class EventoFinanceiroFlow implements EventoProgramavelJava {

    private static final EntityFacade dwFacade = EntityFacadeFactory.getDWFFacade();
    private static final FlowHelperIdds flwHpIdds = new FlowHelperIdds();
    private static JdbcWrapper jdbc = dwFacade.getJdbcWrapper();

    @Override
    public void afterUpdate(PersistenceEvent event) throws Exception {

        DynamicVO dynamicVO = (DynamicVO) event.getVo();
        ModifingFields mdf = event.getModifingFields();
        boolean isBaixando = JapeSession.getPropertyAsBoolean("mov.financeiro.baixando", false).booleanValue();

        if(mdf.isModifing("DHBAIXA") && mdf.getNewValue("DHBAIXA") != null ) {
            System.out.println("___Iniciando EventoFinanceiroFlow___");
            flwHpIdds.finalizaTarefaBaixa(mdf, dynamicVO);
        }

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
                finUpdVO.set("AD_IDINSTPRN", rsCodBarras.getBigDecimal("IDINSTPRN"));
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

    @Override
    public void afterInsert(PersistenceEvent event) throws Exception {
        DynamicVO finVO = (DynamicVO) event.getVo();

        try {

            jdbc = dwFacade.getJdbcWrapper();
            jdbc.openSession();

            NativeSql qryCodBarras = new NativeSql(jdbc);
            qryCodBarras.setReuseStatements(false);
            qryCodBarras.loadSql(FlowHelperIdds.class, "queDadosFinanceiros.sql");
            qryCodBarras.setNamedParameter("NUFIN", finVO.asBigDecimal("NUFIN"));

            ResultSet rsCodBarras = qryCodBarras.executeQuery();
            while (rsCodBarras.next()) {
                JapeWrapper finDAO = JapeFactory.dao(DynamicEntityNames.FINANCEIRO);
                FluidUpdateVO finUpdVO = finDAO.prepareToUpdate(finVO);
                finUpdVO.set("CODBARRA", rsCodBarras.getString("CODBARRAS"));
                finUpdVO.set("AD_IDELEMENTO", rsCodBarras.getString("IDELEMENTO"));
                finUpdVO.set("AD_IDINSTPRN", rsCodBarras.getBigDecimal("IDINSTPRN"));
                finUpdVO.set("AD_NOMETAREFA", rsCodBarras.getString("NOMETAREFA"));
                finUpdVO.set("AD_FATOBSSOLICIT", rsCodBarras.getBigDecimal("AJUSTESTEXT"));
                finUpdVO.set("AD_COMPANX", "S");
                finUpdVO.update();
            }
            rsCodBarras.close();
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            jdbc.closeSession();
        }
    }

    @Override
    public void afterDelete(PersistenceEvent event) throws Exception {

    }

    @Override
    public void beforeCommit(TransactionContext tranCtx) throws Exception {

    }

    @Override
    public void beforeInsert(PersistenceEvent event) throws Exception {

    }

    @Override
    public void beforeUpdate(PersistenceEvent event) throws Exception {

    }

    @Override
    public void beforeDelete(PersistenceEvent event) throws Exception {

    }
}