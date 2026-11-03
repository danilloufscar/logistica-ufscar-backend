package br.com.sglu.service;

import br.com.sglu.model.Cliente;
import br.com.sglu.repository.ClienteRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClienteService {
    private final ClienteRepository repository;

    public ClienteService(ClienteRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Cliente cadastrar(String nome, String telefone, String endereco) {
        return repository.saveAndFlush(new Cliente(nome, telefone, endereco));
    }

    @Transactional(readOnly = true)
    public List<Cliente> listar() {
        return repository.findAll(Sort.by("nome", "id"));
    }
}
