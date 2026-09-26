package club.asbl.asbl_club.membership;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.server.ResponseStatusException;

// The membership rules' outcomes as HTTP answers, for every controller that changes a membership.
// Several conflicts share 409: a stable "code" tells the client which one.
public final class MembershipDecisions {

    private MembershipDecisions() {
    }

    public static ResponseEntity<Void> decided(Runnable action) {
        try {
            action.run();
            return ResponseEntity.noContent().build();
        } catch (NoSuchMemberException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        } catch (LastAdminException e) {
            throw conflict("LAST_ADMIN", "An association needs at least one administrator: appoint another one first.");
        } catch (NotOnYourselfException e) {
            throw conflict("NOT_ON_YOURSELF", "You can't do this to yourself; leave the association instead.");
        }
    }

    private static ErrorResponseException conflict(String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, detail);
        problem.setProperty("code", code);
        return new ErrorResponseException(HttpStatus.CONFLICT, problem, null);
    }
}
