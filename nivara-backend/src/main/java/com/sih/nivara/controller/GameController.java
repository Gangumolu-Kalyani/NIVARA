package com.sih.nivara.controller;

import com.sih.nivara.dto.mapper.GameMapper;
import com.sih.nivara.dto.response.GameResponse;
import com.sih.nivara.service.GameService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Game catalog REST API, read-only. The catalog is system reference data seeded and maintained
 * by migrations (V3), not patient data, so there is no write side.
 *
 * <p>Games are addressed by code, the immutable public key of table games; the table has no uuid.
 */
@RestController
@RequestMapping("/api/games")
public class GameController {

    private final GameService gameService;

    public GameController(GameService gameService) {
        this.gameService = gameService;
    }

    /** The games currently offered to patients, by name. Inactive games are left out. */
    @GetMapping
    public List<GameResponse> findActive() {
        return gameService.findActive().stream()
                .map(GameMapper::toResponse)
                .toList();
    }

    /**
     * One game by code, whether active or not, so an attempt recorded before a game was retired
     * can still show what it was.
     */
    @GetMapping("/{code}")
    public GameResponse findByCode(@PathVariable String code) {
        return GameMapper.toResponse(gameService.findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No game with code " + code)));
    }
}
