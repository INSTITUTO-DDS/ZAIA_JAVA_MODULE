package br.com.zaya.flow.helper;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import com.sankhya.util.UIDGenerator;

import br.com.sankhya.jape.EntityFacade;
import br.com.sankhya.jape.wrapper.JapeFactory;
import br.com.sankhya.jape.wrapper.JapeWrapper;
import br.com.sankhya.jape.wrapper.fluid.FluidCreateVO;
import br.com.sankhya.modelcore.MGEModelException;
import br.com.sankhya.modelcore.util.AgendamentoRelatorioHelper;
import br.com.sankhya.modelcore.util.AgendamentoRelatorioHelper.ParametroRelatorio;
import br.com.sankhya.modelcore.util.DynamicEntityNames;
import br.com.sankhya.modelcore.util.EntityFacadeFactory;
import br.com.sankhya.modelcore.util.SWRepositoryUtils;

public class GerenciadorArquivos {

    private File createFile(File folder, String fileName) throws Exception {
        File file = new File(folder, fileName);

        if (file.exists()) {
            file.delete();
            file.createNewFile();
        } else if (!file.exists()) {
            file.createNewFile();
        }

        return file;
    }

    public void salvarArquivoDeByte(byte[] conteudoBytes, String nomeArquivo) throws Exception {
        File diretorioBase = new File(SWRepositoryUtils.getBaseFolder() + "/Sistema/Anexos/Financeiro/");

        System.out.println("Path do Sistema: " + diretorioBase);

        if (!diretorioBase.exists()) {
            diretorioBase.mkdirs();
        }

        File arquivoSalvo = createFile(diretorioBase, nomeArquivo);
        System.out.println("Arquivo Salvo: " + arquivoSalvo);
        
        try (FileOutputStream fos = new FileOutputStream(arquivoSalvo)) {
            fos.write(conteudoBytes);
        }catch (Exception e) {
            throw new MGEModelException("Erro ao escrever arquivo: " + e.getMessage());
        }
    }

    public void obtemRelatorio(BigDecimal nuFin, BigDecimal nuRef, BigDecimal codusu) throws Exception {

        try {
            System.out.println("--------------------------------");
            System.out.println("Iniciando Metodo de obter relatorio");
            System.out.println("--------------------------------");
            List<Object> lstParam = new ArrayList<Object>();
            EntityFacade dwfFacade = EntityFacadeFactory.getDWFFacade();
            byte[] pdfBytes = null;
            String hash = UIDGenerator.getNextID();

            ParametroRelatorio pk = new ParametroRelatorio("PK_NUFIN",
                    BigDecimal.class.getName(),
                    nuFin);
            lstParam.add(pk);

            System.out.println("-------------Conteudo vindo do parametro relatorio: " + pk);

            pdfBytes = AgendamentoRelatorioHelper.getPrintableReport(nuRef, lstParam,
                    codusu,
                    dwfFacade);
            System.out.println("Conteudo vindo de pdfBytes: " + pdfBytes);
            salvarArquivoDeByte(pdfBytes, hash);
            createAnexo(nuFin, hash, codusu);

        } catch (Exception e) {
            throw new MGEModelException("Erro: " + e.getMessage());
        }
    }

    private void createAnexo(BigDecimal nufin, String hash, BigDecimal codusu) throws Exception {
        JapeWrapper anexoDAO = JapeFactory.dao(DynamicEntityNames.ANEXO_SISTEMA);
        String nomeInstancia = "Financeiro";
        String somaValores = nufin + "_" + nomeInstancia;
        String nomearquivo = "Comprovante_Pagamento_"+nufin+".pdf";
        try {

            FluidCreateVO anexoVO = anexoDAO.create();
            anexoVO.set("NOMEINSTANCIA", "Financeiro");
            anexoVO.set("CHAVEARQUIVO", hash);
            anexoVO.set("NOMEARQUIVO", nomearquivo);
            anexoVO.set("DESCRICAO", nomearquivo);
            anexoVO.set("TIPOAPRES", "GLO");
            anexoVO.set("TIPOACESSO", "ALL");
            anexoVO.set("CODUSU", codusu);
            anexoVO.set("DHALTER", System.currentTimeMillis());
            anexoVO.set("PKREGISTRO", somaValores);
            anexoVO.set("CODUSUALT", codusu);
            anexoVO.set("DHCAD", System.currentTimeMillis());
            anexoVO.save();
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
    }
}
