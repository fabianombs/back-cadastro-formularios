package com.cadastro.fabiano.demo.service;


import com.cadastro.fabiano.demo.dto.request.ClientRequest;
import com.cadastro.fabiano.demo.dto.response.ClientResponse;
import com.cadastro.fabiano.demo.entity.Client;
import com.cadastro.fabiano.demo.mapper.ClientMapper;
import com.cadastro.fabiano.demo.repository.ClientRepository;
import com.cadastro.fabiano.demo.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository repository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final FormTemplateService formTemplateService;


    @Transactional
    public ClientResponse createClient(ClientRequest dto) {

        String resolvedUsername = resolveUsername(dto.username(), dto.company());
        String resolvedName     = (dto.name() != null && !dto.name().isBlank()) ? dto.name() : dto.company();
        String resolvedEmail    = (dto.email() != null && !dto.email().isBlank()) ? dto.email() : resolvedUsername + "@noconta.local";

        if (userRepository.existsByUsername(resolvedUsername)) {
            throw new RuntimeException("Username '" + resolvedUsername + "' já está em uso");
        }

        if (userRepository.existsByEmail(resolvedEmail) && dto.email() != null && !dto.email().isBlank()) {
            throw new RuntimeException("Email '" + resolvedEmail + "' já está em uso");
        }

        ClientRequest effective = new ClientRequest(resolvedName, resolvedEmail, dto.phone(), dto.company(), dto.notes(), resolvedUsername);

        // converte DTO em entidade com User
        Client client = ClientMapper.toEntityWithUser(effective, passwordEncoder);

        Client saved = repository.save(client);

        // converte para DTO de resposta
        return ClientMapper.toDTO(saved);
    }
    public Page<ClientResponse> findAll(Pageable pageable){

        return repository
                .findAll(pageable)
                .map(ClientMapper::toDTO);

    }

    public ClientResponse findById(Long id){

        Client client = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Client not found"));

        return ClientMapper.toDTO(client);
    }

    private String resolveUsername(String username, String company) {
        if (username != null && !username.isBlank()) return username;
        String base = company != null ? company.toLowerCase().replaceAll("[^a-z0-9]", "") : "";
        if (base.isEmpty()) base = "cliente";
        return base + "_" + UUID.randomUUID().toString().substring(0, 6);
    }

    @Transactional
    public void delete(Long id) {
        Client client = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Client not found"));

        // Hard delete de todos os templates do cliente (dados, imagens e arquivos)
        var templates = client.getTemplates();
        if (templates != null) {
            new java.util.ArrayList<>(templates)
                .forEach(t -> formTemplateService.deleteTemplate(t.getId()));
        }

        // Soft delete via JPQL direto — evita que o JPA tente fazer cascade
        // nos templates que já foram hard-deletados acima
        repository.softDelete(id);
    }

}
