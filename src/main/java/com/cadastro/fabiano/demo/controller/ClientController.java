package com.cadastro.fabiano.demo.controller;

import com.cadastro.fabiano.demo.dto.request.ClientRequest;
import com.cadastro.fabiano.demo.dto.response.ClientResponse;
import com.cadastro.fabiano.demo.entity.Client;
import com.cadastro.fabiano.demo.service.ClientService;
import jakarta.validation.Valid;
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

}
