package club.asbl.asbl_club.asbl;

public class SlugAlreadyUsedException extends RuntimeException {

    public SlugAlreadyUsedException(String slug) {
        super("Slug already in use: " + slug);
    }
}
