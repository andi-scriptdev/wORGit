package edu.bsu.worgit.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;

import org.junit.jupiter.api.Test;

import edu.bsu.worgit.exception.InvalidInputException;
import edu.bsu.worgit.exception.InvalidMessageException;
import edu.bsu.worgit.exception.InvalidPasswordException;
import edu.bsu.worgit.exception.InvalidProfileException;

class RulesTest {

    private static final Set<String> INTERESTS =
            Set.of("Technology", "Advocacy", "Academic", "Communication", "Design");
    private static final String EMOJI = "\uD83D\uDE00";   // 1 character for PostgreSQL, 2 UTF-16 units in Java

    @Test
    void newPasswordNeedsEightCharacters() {
        assertThatThrownBy(() -> Rules.requireValidNewPassword("abc1234"))
                .isInstanceOf(InvalidPasswordException.class)
                .hasMessage("Password must be at least 8 characters");
        assertThatThrownBy(() -> Rules.requireValidNewPassword(null)).isInstanceOf(InvalidPasswordException.class);
        assertThatCode(() -> Rules.requireValidNewPassword("abcd1234")).doesNotThrowAnyException();
    }

    @Test
    void emptyMessageIsRejected() {
        assertThatThrownBy(() -> Rules.requireValidMessage("   "))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("Message cannot be empty.");
        assertThatThrownBy(() -> Rules.requireValidMessage(null)).isInstanceOf(InvalidMessageException.class);
    }

    @Test
    void messageIsLimitedToThreeHundredCharacters() {
        assertThat(Rules.requireValidMessage("x".repeat(300))).hasSize(300);
        assertThatThrownBy(() -> Rules.requireValidMessage("x".repeat(312)))
                .isInstanceOf(InvalidMessageException.class)
                .hasMessage("Max 300 characters (312/300).");
    }

    @Test
    void messageLengthCountsCharactersLikeTheDatabase() {
        assertThatCode(() -> Rules.requireValidMessage(EMOJI.repeat(300))).doesNotThrowAnyException();
        assertThatThrownBy(() -> Rules.requireValidMessage(EMOJI.repeat(301)))
                .hasMessage("Max 300 characters (301/300).");
    }

    @Test
    void messageIsTrimmedBeforeStoring() {
        assertThat(Rules.requireValidMessage("  hello  ")).isEqualTo("hello");
    }

    @Test
    void displayNameIsOneToThirtyCharacters() {
        assertThatThrownBy(() -> Rules.requireValidDisplayName(" ")).isInstanceOf(InvalidProfileException.class);
        assertThatThrownBy(() -> Rules.requireValidDisplayName("a".repeat(31)))
                .isInstanceOf(InvalidProfileException.class);
        assertThat(Rules.requireValidDisplayName("a")).isEqualTo("a");
        assertThat(Rules.requireValidDisplayName("a".repeat(30))).hasSize(30);
    }

    @Test
    void bioIsAtMostOneHundredFiftyCharacters() {
        assertThat(Rules.requireValidBio("b".repeat(150))).hasSize(150);
        assertThatThrownBy(() -> Rules.requireValidBio("b".repeat(180)))
                .isInstanceOf(InvalidProfileException.class)
                .hasMessage("Bio must be 150 characters or fewer");
        assertThat(Rules.requireValidBio(null)).isEmpty();
    }

    @Test
    void atLeastOneInterestFromTheList() {
        assertThatThrownBy(() -> Rules.requireInterests(List.of(), INTERESTS))
                .isInstanceOf(InvalidInputException.class)
                .hasMessage("Pick at least one interest.");
        assertThatThrownBy(() -> Rules.requireInterests(List.of("Sports"), INTERESTS))
                .isInstanceOf(InvalidInputException.class);
        assertThat(Rules.requireInterests(List.of("Design", "Design", "Technology"), INTERESTS))
                .isEqualTo(new LinkedHashSet<>(List.of("Design", "Technology")));
    }

    @Test
    void organizationTextIsLimitedToTwoHundredFiftyCharacters() {
        assertThat(Rules.requireOrgText("Description", "description", "d".repeat(250))).hasSize(250);
        assertThatThrownBy(() -> Rules.requireOrgText("Description", "description", "d".repeat(251)))
                .isInstanceOf(InvalidInputException.class)
                .hasMessage("Description must be 250 characters or fewer");
        assertThat(Rules.requireOrgText("Description", "description", null)).isNull();
    }

    @Test
    void scoreIsOrgsTimesTenPlusMessagesTimesTwoPlusPeersTimesFive() {
        assertThat(Rules.engagementScore(4, 128, 23)).isEqualTo(411);   // the wireframe's sample
        assertThat(Rules.engagementScore(0, 0, 0)).isZero();
    }

    @Test
    void participationRateIsAttendedOverJoinedEvents() {
        assertThat(Rules.participationRate(1, 2)).isEqualTo(OptionalInt.of(50));
        assertThat(Rules.participationRate(2, 3)).isEqualTo(OptionalInt.of(67));
        assertThat(Rules.participationRate(0, 0)).isEmpty();   // shown as "-"
    }

    @Test
    void achievementsFollowTheOneFiveTenMilestones() {
        assertThat(Achievements.earned(0)).isEmpty();
        assertThat(Achievements.earned(5)).extracting(Achievements.Milestone::title)
                .containsExactly("First Event", "5 Events Attended");
        assertThat(Achievements.earned(10)).hasSize(3);
        var next = Achievements.next(5).orElseThrow();
        assertThat(next.title()).isEqualTo("10 Events Attended");
        assertThat(Achievements.progress(5, next)).isEqualTo("5 of 10 done");
        assertThat(Achievements.next(10)).isEmpty();
    }

    @Test
    void messagesMatchTheSpecWording() {
        assertThat(Messages.roomAccess("Developers Guild"))
                .isEqualTo("You need to be a member of Developers Guild to enter this room.");
        assertThat(Messages.alreadyMember("Developers Guild")).isEqualTo("You are already a member of Developers Guild.");
        assertThat(Messages.alreadyConnected("Mika S.")).isEqualTo("You are already connected with Mika S.");
        assertThat(Messages.alreadyConnected("Mika")).isEqualTo("You are already connected with Mika.");
        assertThat(Messages.notEligible("Sam Perez"))
                .isEqualTo("Sam Perez has no recorded attendance, so no certificate was created.");
        assertThat(Messages.AUTH_FAILED).isEqualTo("ID or password is incorrect");
        assertThat(Messages.DATA_STORE).isEqualTo("We could not reach the database. Try again.");
    }
}
