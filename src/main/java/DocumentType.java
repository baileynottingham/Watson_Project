/*
 Bailey Nottingham
 */

public class DocumentType {
    String title = "";
    String data = " ";

    public DocumentType(String title) {
        // remove front [[ and back ]] from title
        this.title = title.substring(2);
        this.title = getTitle().substring(0, getTitle().length() - 2);
    }

    public void concatData(String str) {
        data += str + " ";
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public void removeUnecessaryCharacters() {
        this.data = data.replaceAll("\\[tpl\\](.*?)\\[\\/tpl]", "");
        this.data = data.replaceAll("(==*?)", "");
    }
}
