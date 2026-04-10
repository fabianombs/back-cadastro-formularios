package com.cadastro.fabiano.demo.controller;

import com.cadastro.fabiano.demo.dto.request.ClientRequest;
import com.cadastro.fabiano.demo.dto.response.ClientResponse;
import com.cadastro.fabiano.demo.dto.response.FormTemplateResponse;
import com.cadastro.fabiano.demo.service.ClientService;
import com.cadastro.fabiano.demo.service.FormTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/clients")
@RequiredArgsConstructor
@Tag(name = "Clientes", description = "Gerenciamento de clientes e seus formulários")
public class ClientController {

    private final ClientService service;
    private final FormTemplateService formTemplateService;

    @PostMapping
    @Operation(summary = "Criar cliente", description = "Cria um novo cliente com usuário associado")
    @ApiResponse(responseCode = "200", description = "Cliente criado com sucesso")
    public ResponseEntity<ClientResponse> create(@RequestBody ClientRequest dto) {
        ClientResponse response = service.createClient(dto);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "Listar clientes", description = "Retorna todos os clientes ativos paginados")
    public Page<ClientResponse> findAll(Pageable pageable) {
        return service.findAll(pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Buscar cliente por ID")
    @ApiResponse(responseCode = "200", description = "Cliente encontrado")
    @ApiResponse(responseCode = "400", description = "Cliente não encontrado")
    public ClientResponse findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Excluir cliente (soft delete)", description = "Realiza soft delete do cliente e de todos os seus templates")
    @ApiResponse(responseCode = "200", description = "Cliente excluído")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @GetMapping("/{id}/templates")
    @SecurityRequirements
    @Operation(summary = "Listar templates do cliente", description = "Endpoint público — retorna os formulários ativos de um cliente pelo ID")
    public Page<FormTemplateResponse> getTemplatesByClient(@PathVariable Long id, Pageable pageable) {
        return formTemplateService.findTemplatesByClientId(id, pageable);
    }
}
