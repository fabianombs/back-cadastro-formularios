package com.cadastro.fabiano.demo.controller;

import com.cadastro.fabiano.demo.dto.request.ClientRequest;
import com.cadastro.fabiano.demo.dto.response.ClientResponse;
import com.cadastro.fabiano.demo.dto.response.FormTemplateResponse;
import com.cadastro.fabiano.demo.service.ClientService;
import com.cadastro.fabiano.demo.service.FormTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService service;
    private final FormTemplateService formTemplateService;

    @PostMapping
    public ResponseEntity<ClientResponse> create(@RequestBody ClientRequest dto) {
        ClientResponse response = service.createClient(dto);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public Page<ClientResponse> findAll(Pageable pageable){
        return service.findAll(pageable);
    }

    @GetMapping("/{id}")
    public ClientResponse findById(@PathVariable Long id){
        return service.findById(id);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id){
        service.delete(id);
    }

    /** Endpoint público: retorna os templates de um cliente pelo ID (usado no link público do formulário) */
    @GetMapping("/{id}/templates")
    public Page<FormTemplateResponse> getTemplatesByClient(@PathVariable Long id, Pageable pageable) {
        return formTemplateService.findTemplatesByClientId(id, pageable);
    }

}
