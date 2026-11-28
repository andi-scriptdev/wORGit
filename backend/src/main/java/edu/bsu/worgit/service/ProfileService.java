package edu.bsu.worgit.service;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.bsu.worgit.domain.AuthenticatedUser;
import edu.bsu.worgit.domain.PeopleRows;
import edu.bsu.worgit.domain.Permissions;
import edu.bsu.worgit.domain.Rules;
import edu.bsu.worgit.exception.NotFoundException;
import edu.bsu.worgit.repository.OrganizationRepository;
import edu.bsu.worgit.repository.ProfileRepository;

/** ST-08 My Profile: display name, bio, interests and the "visible in peer matching" toggle. */
@Service
public class ProfileService {

    private final ProfileRepository profiles;
    private final OrganizationRepository orgs;

    public ProfileService(ProfileRepository profiles, OrganizationRepository orgs) {
        this.profiles = profiles;
        this.orgs = orgs;
    }

    public PeopleViews.MyProfile get(AuthenticatedUser student) {
        Permissions.requireStudent(student);
        return toView(requireProfile(student.userId()));
    }

    /**
     * Saves the whole form. Display name 1-30 and bio up to 150 characters, at least one interest from the list.
     * Nothing is saved when any field is invalid.
     */
    @Transactional
    public PeopleViews.MyProfile update(AuthenticatedUser student, PeopleViews.ProfileInput input) {
        Permissions.requireStudent(student);
        PeopleRows.StudentProfile current = requireProfile(student.userId());
        String displayName = Rules.requireValidDisplayName(input.displayName());
        String bio = Rules.requireValidBio(input.bio());
        Set<String> interests = Rules.requireInterests(input.interests(), orgs.allowedInterests());
        boolean visible = input.visibleInMatching() == null ? current.visibleInMatching() : input.visibleInMatching();

        profiles.updateStudentProfile(student.userId(), displayName, bio, visible);
        profiles.replaceStudentInterests(student.userId(), interests);
        return toView(requireProfile(student.userId()));
    }

    private PeopleRows.StudentProfile requireProfile(String studentId) {
        return profiles.findStudentProfile(studentId).orElseThrow(NotFoundException::new);
    }

    private PeopleViews.MyProfile toView(PeopleRows.StudentProfile p) {
        List<String> all = orgs.allowedInterests().stream().sorted().toList();
        return new PeopleViews.MyProfile(p.userId(), p.fullName(), p.displayName(), p.email(), p.bio(),
                p.interests(), p.visibleInMatching(), all);
    }
}
