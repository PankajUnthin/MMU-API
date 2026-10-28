package com.iemr.mmu.controller.nikshay;

import com.iemr.mmu.service.nikshay.NikshayGenerateService;
import com.iemr.mmu.utils.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.format.DateTimeFormatter;
import java.util.Map;


@RestController
@RequestMapping(value = "/nikshay")
public class NikshayIdGenrationController {
    private static final Logger logger = LoggerFactory.getLogger(com.iemr.mmu.controller.stoptb.NikshayExportController.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    @Autowired
    private NikshayGenerateService nikshayGenerateService;

    @Autowired
    private JwtUtil jwtUtil;

    /** Best-effort — this is only used for a created_by/modified_by audit column,
     * never for authorization (the filter chain/PreAuthorize already handled that). */
    private String currentUsername(HttpServletRequest request) {
        try {
            String header = request.getHeader("Authorization");
            if (header == null) {
                return "unknown";
            }
            String token = header.startsWith("Bearer ") ? header.substring(7) : header;
            String username = jwtUtil.extractUsername(token);
            return username != null ? username : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    @Operation(summary = "Generate Nikshay IDs for a beneficiaries CSV via Playwright login + AddIndiaTB "
            + "(runs the in-repo nikshay-enroll Node worker on the API host)")
    @PostMapping(value = "/generateIds", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> generateIds(@RequestBody Map<String, String> body) {
        String username = body != null ? body.get("username") : null;
        String password = body != null ? body.get("password") : null;
        String csv = body != null ? body.get("csv") : null;
        String env = body != null ? body.get("env") : "uat";
        try {
            Map<String, Object> result = nikshayGenerateService.generateIds(username, password, csv, env);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalStateException e) {
            logger.warn("Nikshay generateIds failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            logger.error("Error generating Nikshay IDs", e);
            return ResponseEntity.status(500).body("Could not generate Nikshay IDs");
        }
    }
}
