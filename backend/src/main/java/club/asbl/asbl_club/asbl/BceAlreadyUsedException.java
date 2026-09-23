package club.asbl.asbl_club.asbl;

public class BceAlreadyUsedException extends RuntimeException {

    public BceAlreadyUsedException(String bceNumber) {
        super("BCE number already in use: " + bceNumber);
    }
}
