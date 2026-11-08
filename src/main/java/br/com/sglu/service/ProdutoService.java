package br.com.sglu.service;

import br.com.sglu.exception.CodigoProdutoJaCadastradoException;
import br.com.sglu.model.Produto;
import br.com.sglu.repository.ProdutoRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProdutoService {
    private final ProdutoRepository repository;

    public ProdutoService(ProdutoRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Produto cadastrar(String codigo, String nome, Integer estoqueMinimo) {
        String codigoNormalizado = Produto.normalizarCodigo(codigo);
        if (repository.existsByCodigo(codigoNormalizado)) throw new CodigoProdutoJaCadastradoException();
        // A restrição UNIQUE também garante unicidade em cadastros concorrentes.
        return repository.saveAndFlush(new Produto(codigoNormalizado, nome, estoqueMinimo));
    }

    @Transactional(readOnly = true)
    public List<Produto> listar() {
        return repository.findAll(Sort.by("nome", "id"));
    }
}
