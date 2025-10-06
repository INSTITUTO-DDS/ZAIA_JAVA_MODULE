package br.com.zaya.flow.events;

import br.com.sankhya.extensions.eventoprogramavel.EventoProgramavelJava;
import br.com.sankhya.jape.event.PersistenceEvent;
import br.com.sankhya.jape.event.TransactionContext;
import br.com.sankhya.jape.vo.DynamicVO;
import br.com.sankhya.jape.wrapper.JapeFactory;
import br.com.sankhya.jape.wrapper.JapeWrapper;
import br.com.sankhya.jape.wrapper.fluid.FluidUpdateVO;
import br.com.sankhya.modelcore.MGEModelException;
import br.com.sankhya.modelcore.util.DynamicEntityNames;

import java.math.BigDecimal;
import java.util.Collection;

public class AtualizaCodBarras implements EventoProgramavelJava {
    @Override
    public void beforeInsert(PersistenceEvent event) throws Exception {

    }

    @Override
    public void beforeUpdate(PersistenceEvent event) throws Exception {

    }

    @Override
    public void beforeDelete(PersistenceEvent event) throws Exception {

    }

    @Override
    public void afterInsert(PersistenceEvent event) throws Exception {
        DynamicVO finVO = (DynamicVO) event.getVo();
        BigDecimal nuNota = finVO.asBigDecimal("NUNOTA");
        DynamicVO tnfCabVO = JapeFactory.dao(DynamicEntityNames.CABECALHO_NOTA).findByPK(nuNota);
        BigDecimal idInstPrn = tnfCabVO.asBigDecimal("AD_IDINSTPRN");

        if(finVO.asString("PROVISAO").contains("N")) {

            try {
                Collection<DynamicVO> fatConNotasVOs = JapeFactory.dao("AD_FATCONNOTAS").find("this.IDINSTPRN = ?", idInstPrn);
                for (DynamicVO fatConNotasVO : fatConNotasVOs) {
                    if ( fatConNotasVO.asString("CODBARRAS") != null ) {
                        finVO.setProperty("CODBARRA", fatConNotasVO.asString("CODBARRAS"));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw new MGEModelException(e.getMessage());
            }
        }
    }

    @Override
    public void afterUpdate(PersistenceEvent event) throws Exception {

    }

    @Override
    public void afterDelete(PersistenceEvent event) throws Exception {

    }

    @Override
    public void beforeCommit(TransactionContext tranCtx) throws Exception {

    }
}
