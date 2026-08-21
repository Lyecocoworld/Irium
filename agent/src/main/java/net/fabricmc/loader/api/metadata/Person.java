package net.fabricmc.loader.api.metadata;

/**
 * Adaptateur Irium — personne (auteur/contributeur).
 * M7-B11e : fabric-loader 0.17 a changé la signature — getContact() retourne
 * ContactInformation (plus Map<String,String>). Mod Menu 20.0.1 fait un
 * invokeinterface ()ContactInformation -> NoSuchMethodError avec l'ancienne.
 */
public interface Person {

    String getName();

    default ContactInformation getContact() {
        return new ContactInformation() {
            @Override public java.util.Optional<String> get(String key) { return java.util.Optional.empty(); }
        };
    }
}
