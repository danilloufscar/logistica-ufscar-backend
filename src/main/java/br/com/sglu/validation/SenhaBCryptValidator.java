package br.com.sglu.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

public class SenhaBCryptValidator implements ConstraintValidator<SenhaBCrypt, String> {
    @Override
    public boolean isValid(String senha, ConstraintValidatorContext context) {
        // A obrigatoriedade é validada separadamente por @NotBlank.
        return senha == null || senha.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
