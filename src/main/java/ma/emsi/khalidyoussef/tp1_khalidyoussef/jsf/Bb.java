package ma.emsi.khalidyoussef.tp1_khalidyoussef.jsf;

import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.model.SelectItem;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import ma.emsi.khalidyoussef.tp1_khalidyoussef.llm.JsonUtilPourGemini;
import ma.emsi.khalidyoussef.tp1_khalidyoussef.llm.LlmInteraction;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Backing bean pour la page JSF index.xhtml.
 * Portée view pour conserver l'état de la conversation qui dure pendant plusieurs requêtes HTTP.
 * La portée view nécessite l'implémentation de Serializable (le backing bean peut être mis en mémoire secondaire).
 */
@Named
@ViewScoped
public class Bb implements Serializable {

    @Inject
    private JsonUtilPourGemini jsonUtil;
    /**
     * JSON envoyé à l’API (la requête).
     */
    private String texteRequeteJson;

    /**
     * JSON reçu de l’API (la réponse complète).
     */
    private String texteReponseJson;


    // Mode debug
    private boolean debug = false;

    private String reponseJson;


    /**
     * Rôle "système" que l'on attribuera plus tard à un LLM.
     * Valeur par défaut que l'utilisateur peut modifier.
     * Possible d'écrire un nouveau rôle dans la liste déroulante.
     */
    private String roleSysteme;

    /**
     * Quand le rôle est choisi par l'utilisateur dans la liste déroulante,
     * il n'est plus possible de le modifier (voir code de la page JSF), sauf si on veut un nouveau chat.
     */
    private boolean roleSystemeChangeable = true;

    /**
     * Liste de tous les rôles de l'API prédéfinis.
     */
    private List<SelectItem> listeRolesSysteme;

    /**
     * Dernière question posée par l'utilisateur.
     */
    private String question;
    /**
     * Dernière réponse de l'API OpenAI.
     */
    private String reponse;
    /**
     * La conversation depuis le début.
     */
    private StringBuilder conversation = new StringBuilder();

    /**
     * Contexte JSF. Utilisé pour qu'un message d'erreur s'affiche dans le formulaire.
     */
    @Inject
    private FacesContext facesContext;
    /**
     * Obligatoire pour un bean CDI (classe gérée par CDI), s'il y a un autre constructeur.
     */
    public Bb() {
        this.setDebug(false);
    }

    public String getRoleSysteme() {
        return roleSysteme;
    }

    public void setRoleSysteme(String roleSysteme) {
        this.roleSysteme = roleSysteme;
    }

    public String getReponseJson() {
        return this.reponse;
    }

    public void setReponseJson(String reponseJson) {
        this.reponseJson = reponseJson;
    }

    public boolean isRoleSystemeChangeable() {
        return roleSystemeChangeable;
    }

    public String getTexteRequeteJson() {
        return texteRequeteJson;
    }

    public void setTexteRequeteJson(String texteRequeteJson) {
        this.texteRequeteJson = texteRequeteJson;
    }

    public String getTexteReponseJson() {
        return texteReponseJson;
    }

    public void setTexteReponseJson(String texteReponseJson) {
        this.texteReponseJson = texteReponseJson;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getReponse() {
        return reponse;
    }

    /**
     * setter indispensable pour le textarea.
     *
     * @param reponse la réponse à la question.
     */
    public void setReponse(String reponse) {
        this.reponse = reponse;
    }

    public String getConversation() {
        return conversation.toString();
    }

    public void setConversation(String conversation) {
        this.conversation = new StringBuilder(conversation);
    }

    public boolean isDebug() {
        return debug;
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    /**
     * Envoie la question au serveur.
     * En attendant de l'envoyer à un LLM, le serveur fait un traitement quelconque, juste pour tester :
     * Le traitement consiste à copier la question en minuscules et à l'entourer avec "||". Le rôle système
     * est ajouté au début de la première réponse.
     *
     * @return null pour rester sur la même page.
     */
    public String envoyer() {
        if (question == null || question.isBlank()) {
            // Erreur ! Le formulaire va être réaffiché en réponse à la requête POST, avec un message d'erreur.
            FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR,
                    "Texte question vide", "Il manque le texte de la question");
            facesContext.addMessage(null, message);
            return null;
        }

        try {
            // Vérification avant l’envoi
            if (jsonUtil == null) {
                throw new IllegalStateException("JsonUtilPourGemini n’est pas injecté.");
            }

            // 🔧 FIX: Set the system role before sending the request
            if (roleSysteme != null && !roleSysteme.isBlank()) {
                jsonUtil.setSystemRole(roleSysteme);
            } else {
                // Default role if none is selected
                jsonUtil.setSystemRole("You are a helpful assistant.");
            }

            // 🔍 Tracer le texte envoyé (utile pour le debug)
            if (debug) {
                System.out.println("📤 Envoi au LLM : " + question);
            }
            LlmInteraction interaction = jsonUtil.envoyerRequete(question);
            this.reponse = interaction.reponseExtraite();
            this.texteRequeteJson = interaction.questionJson();
            this.texteReponseJson = interaction.reponseJson();
        } catch (Exception e) {
            FacesMessage message =
                    new FacesMessage(FacesMessage.SEVERITY_ERROR,
                            "Problème de connexion avec l'API du LLM",
                            "Problème de connexion avec l'API du LLM" + e.getMessage());
            facesContext.addMessage(null, message);
        }
        // La conversation contient l'historique des questions-réponses depuis le début.
        afficherConversation();
        return null;
    }

    /**
     * Pour un nouveau chat.
     * Termine la portée view en retournant "index" (la page index.xhtml sera affichée après le traitement
     * effectué pour construire la réponse) et pas null. null aurait indiqué de rester dans la même page (index.xhtml)
     * sans changer de vue.
     * Le fait de changer de vue va faire supprimer l'instance en cours du backing bean par CDI et donc on reprend
     * tout comme au début puisqu'une nouvelle instance du backing va être utilisée par la page index.xhtml.
     * @return "index"
     */
    public String nouveauChat() {
        return "index";
    }

    /**
     * Pour afficher la conversation dans le textArea de la page JSF.
     */
    private void afficherConversation() {
        this.conversation
                .append("== User:\n")
                .append(question)
                .append("\n== Serveur:\n")
                .append(reponse)
                .append("\n");
    }


    public List<SelectItem> getRolesSysteme() {
        if (this.listeRolesSysteme == null) {
            // Génère les rôles de l'API prédéfinis
            this.listeRolesSysteme = new ArrayList<>();
            // Vous pouvez évidemment écrire ces rôles dans la langue que vous voulez.
            String role = """
                    You are a helpful assistant. You help the user to find the information they need.
                    If the user type a question, you answer it.
                    """;
            // 1er argument : la valeur du rôle, 2ème argument : le libellé du rôle
            this.listeRolesSysteme.add(new SelectItem(role, "Assistant"));

            role = """
                    You are an interpreter. You translate from English to French and from French to English.
                    If the user type a French text, you translate it into English.
                    If the user type an English text, you translate it into French.
                    If the text contains only one to three words, give some examples of usage of these words in English.
                    """;
            this.listeRolesSysteme.add(new SelectItem(role, "Traducteur Anglais-Français"));

            role = """
                    Your are a travel guide. If the user type the name of a country or of a town,
                    you tell them what are the main places to visit in the country or the town
                    are you tell them the average price of a meal.
                    """;
            this.listeRolesSysteme.add(new SelectItem(role, "Guide touristique"));

            role = """
                    You are an overly enthusiastic motivational coach who SCREAMS encouragement.
                    Respond to every question with EXTREME MOTIVATION and CAPITAL LETTERS.
                    Use phrases like "TU ES UN CHAMPION!" and "RIEN N'EST IMPOSSIBLE!"
                    End every response with "💪 ALLEZ, C'EST PARTI Champion! 🔥"
                    """;
            this.listeRolesSysteme.add(new SelectItem(role, "Coach Motivateur"));

            role = """
                    You are an overly passionate chef who relates everything to cooking and food.
                    Whatever the user asks, you explain it using culinary metaphors and recipes.
                    Use expressions like "C'est comme préparer un tajine..." or "La vie est une recette..."
                    Always include at least one Moroccan dish reference.
                    End with "Bon appétit!" or "Sahha!"
                    """;
            this.listeRolesSysteme.add(new SelectItem(role, "Chef Cuisinier Fou"));

            role = """
                    You are a highly intelligent but extremely sarcastic professor.
                    Answer questions correctly but with heavy sarcasm and irony.
                    Use phrases like "Oh, quelle question brillante..." or "Évidemment, comme tout le monde le sait..."
                    Be condescending but funny, never mean-spirited.
                    End with a sarcastic remark about the question itself.
                    """;
            this.listeRolesSysteme.add(new SelectItem(role, "Professeur Sarcastique"));

            /*role = """
                    You are a loving Moroccan grandmother who gives advice with warmth and wisdom.
                    Mix French, Arabic expressions, and Darija naturally.
                    Always relate advice to family, food, and traditional Moroccan values.
                    Use terms like "ya weldi" (my son), "benti" (my daughter), "Allah y'hassnek".
                    Include references to Moroccan dishes and proverbs.
                    End with blessings like "Allah ykhalik" or "Rabi m3ak".
                    """;
            this.listeRolesSysteme.add(new SelectItem(role, "Grand-mère Marocaine"));*/

            role = """
                You are a romantic poet from the 19th century who answers everything in poetic verse.
                Respond with beautiful, flowing French using rich vocabulary and metaphors.
                Structure responses like poetry with rhythm and emotion.
                Use romantic imagery: roses, moonlight, stars, the sea, love.
                Include expressions like "Ô tendre ami", "Dans les méandres de...", "Tel un souffle..."
                End with a poetic closing like "Ainsi parle le cœur..." or "Et la plume se tait..."
                """;
            this.listeRolesSysteme.add(new SelectItem(role, "Poète Romantique"));

        }

        return this.listeRolesSysteme;
    }

    public void toggleDebug() {
        this.setDebug(!isDebug());
    }

}