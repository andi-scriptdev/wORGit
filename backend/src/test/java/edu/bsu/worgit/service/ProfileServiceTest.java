package edu.bsu.worgit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import edu.bsu.worgit.domain.AuthenticatedUser;
import edu.bsu.worgit.domain.Role;
import edu.bsu.worgit.exception.ForbiddenException;
import edu.bsu.worgit.exception.InvalidInputException;
import edu.bsu.worgit.exception.InvalidProfileException;
import edu.bsu.worgit.support.FakePeople;
import edu.bsu.worgit.support.FakeWorld;

class ProfileServiceTest {

    private FakeWorld w;
    private FakePeople p;
    private ProfileService svc;
    private AuthenticatedUser andrea;

    @BeforeEach
    void setUp() {
        w = new FakeWorld();
        p = new FakePeople(w);
        andrea = p.student("25-09041", "Andrea", true, "Technology");
        svc = new ProfileService(p, w);
    }

    private static PeopleViews.ProfileInput input(String name, String bio, List<String> interests, Boolean visible) {
        return new PeopleViews.ProfileInput(name, bio, interests, visible);
    }

    @Test
    void profileShowsOwnDataAndTheFiveInterestChips() {
        PeopleViews.MyProfile me = svc.get(andrea);
        assertThat(me.userId()).isEqualTo("25-09041");
        assertThat(me.email()).isEqualTo("25-09041@g.batstate-u.edu.ph");
        assertThat(me.interests()).containsExactly("Technology");
        assertThat(me.allInterests()).containsExactly("Academic", "Advocacy", "Communication", "Design", "Technology");
    }

    @Test
    void savingUpdatesNameBioInterestsAndVisibility() {
        PeopleViews.MyProfile me = svc.update(andrea, input("  Andi  ", "Hello", List.of("Design", "Academic"), false));
        assertThat(me.displayName()).isEqualTo("Andi");
        assertThat(me.bio()).isEqualTo("Hello");
        assertThat(me.interests()).containsExactly("Academic", "Design");
        assertThat(me.visibleInMatching()).isFalse();
    }

    @Test
    void visibilityIsKeptWhenNotSent() {
        assertThat(svc.update(andrea, input("Andrea", "", List.of("Design"), null)).visibleInMatching()).isTrue();
    }

    @Test
    void bioOver150CharactersUsesTheSpecMessage() {
        assertThatThrownBy(() -> svc.update(andrea, input("Andrea", "x".repeat(151), List.of("Design"), null)))
                .isInstanceOf(InvalidProfileException.class)
                .hasMessage("Bio must be 150 characters or fewer");
        assertThat(svc.get(andrea).interests()).containsExactly("Technology");   // nothing saved
    }

    @Test
    void atLeastOneInterestFromTheList() {
        assertThatThrownBy(() -> svc.update(andrea, input("Andrea", "", List.of(), null)))
                .isInstanceOf(InvalidInputException.class)
                .hasMessage("Pick at least one interest.");
        assertThatThrownBy(() -> svc.update(andrea, input("Andrea", "", List.of("Cooking"), null)))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void displayNameMustBe1To30Characters() {
        assertThatThrownBy(() -> svc.update(andrea, input("   ", "", List.of("Design"), null)))
                .isInstanceOf(InvalidProfileException.class);
        assertThatThrownBy(() -> svc.update(andrea, input("x".repeat(31), "", List.of("Design"), null)))
                .isInstanceOf(InvalidProfileException.class);
    }

    @Test
    void staffHaveNoStudentProfile() {
        AuthenticatedUser adviser = w.addStaff("ADM-003", Role.FACULTY_ADVISER);
        assertThatThrownBy(() -> svc.get(adviser)).isInstanceOf(ForbiddenException.class);
    }
}
