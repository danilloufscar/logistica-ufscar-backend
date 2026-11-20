package br.com.sglu.model;

public enum TipoOcorrencia {
    ATRASO("Atraso"),
    AVARIA("Avaria"),
    DESTINATARIO_AUSENTE("Destinatário ausente");

    private final String rotulo;

    TipoOcorrencia(String rotulo) {
        this.rotulo = rotulo;
    }

    public String getRotulo() {
        return rotulo;
    }
}