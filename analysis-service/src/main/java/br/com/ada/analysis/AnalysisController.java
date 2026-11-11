package br.com.ada.analysis;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/analises")
public class AnalysisController {
    private final AnalysisService service;

    public AnalysisController(AnalysisService service) {
        this.service = service;
    }

    @GetMapping("/{transactionId}")
    public AnalysisResponse findByTransactionId(@PathVariable UUID transactionId) {
        AnalysisResponse result = service.findByTransactionId(transactionId);
        if (result == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Análise ainda não encontrada");
        }
        return result;
    }
}
