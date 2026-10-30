package br.com.sglu.dto.auth;

public record LoginResponse(String token) {
    @Override
    public String toString() {
        return "LoginResponse[token omitido]";
    }
}
