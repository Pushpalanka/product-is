/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wso2.is.portal.user.client.api;

import org.osgi.framework.BundleContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.identity.mgt.AuthenticationContext;
import org.wso2.carbon.identity.mgt.Group;
import org.wso2.carbon.identity.mgt.RealmService;
import org.wso2.carbon.identity.mgt.User;
import org.wso2.carbon.identity.mgt.UserState;
import org.wso2.carbon.identity.mgt.bean.UserBean;
import org.wso2.carbon.identity.mgt.claim.Claim;
import org.wso2.carbon.identity.mgt.claim.MetaClaim;
import org.wso2.carbon.identity.mgt.exception.AuthenticationFailure;
import org.wso2.carbon.identity.mgt.exception.GroupNotFoundException;
import org.wso2.carbon.identity.mgt.exception.IdentityStoreException;
import org.wso2.carbon.identity.mgt.exception.UserNotFoundException;
import org.wso2.carbon.identity.mgt.impl.util.IdentityMgtConstants;
import org.wso2.carbon.kernel.utils.StringUtils;
import org.wso2.is.portal.user.client.api.bean.UUFUser;
import org.wso2.is.portal.user.client.api.bean.UserListBean;
import org.wso2.is.portal.user.client.api.exception.UserPortalUIException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.PasswordCallback;

/**
 * Identity store client service implementation.
 */
@Component(
        name = "org.wso2.is.portal.user.client.api.IdentityStoreClientServiceImpl",
        service = IdentityStoreClientService.class,
        immediate = true)
public class IdentityStoreClientServiceImpl implements IdentityStoreClientService {

    private static final Logger LOGGER = LoggerFactory.getLogger(IdentityStoreClientServiceImpl.class);
    private static final String USERNAME_CLAIM = "http://wso2.org/claims/username";
    private static final String GROUPNAME_CLAIM = "http://wso2.org/claims/groupname";
    private static final int MAX_RECORD_LENGTH = 500;
    private static final String LOCKED_STATE = "LOCKED";
    private static final String DISABLED_STATE = "DISABLED";
    private static final String UNLOCKED_STATE = "UNLOCKED";

    private RealmService realmService;

    @Activate
    protected void start(final BundleContext bundleContext) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("IdentityStoreClientService activated successfully.");
        }
    }

    @Reference(
            name = "realmService",
            service = RealmService.class,
            cardinality = ReferenceCardinality.MANDATORY,
            policy = ReferencePolicy.DYNAMIC,
            unbind = "unsetRealmService")
    protected void setRealmService(RealmService realmService) {

        this.realmService = realmService;
    }

    protected void unsetRealmService(RealmService realmService) {

        this.realmService = null;
    }

    @Override
    public UUFUser authenticate(String username, char[] password, String domain) throws UserPortalUIException {

        try {
            //TODO if different claim is used, need identify that claim.
            Claim usernameClaim = new Claim(IdentityMgtConstants.CLAIM_ROOT_DIALECT,
                    IdentityMgtConstants.USERNAME_CLAIM, username);
            PasswordCallback passwordCallback = new PasswordCallback("password", false);
            passwordCallback.setPassword(password);
            AuthenticationContext authenticationContext = getRealmService().getIdentityStore()
                    .authenticate(usernameClaim, new Callback[]{passwordCallback}, domain);

            if (authenticationContext.isAuthenticated()) {
                User identityUser = authenticationContext.getUser();

                //TODO if another claim used, need to load username claim
                return new UUFUser(username, identityUser.getUniqueUserId(), identityUser.getDomainName());
            }
        } catch (AuthenticationFailure e) {
            String error = "Invalid credentials.";
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(error, e);
            }
            throw new UserPortalUIException("Invalid credentials.");
        } catch (IdentityStoreException e) {
            String error = "Failed to authenticate user.";
            LOGGER.error(error, e);
            throw new UserPortalUIException(error);
        }
        throw new UserPortalUIException("Invalid credentials.");
    }

    @Override
    public void updatePassword(String username, char[] oldPassword, char[] newPassword, String domain)
            throws UserNotFoundException, UserPortalUIException {

        try {
            //validate the old password
            UUFUser uufUser = authenticate(username, oldPassword, domain);

            PasswordCallback passwordCallback = new PasswordCallback("password", false);
            passwordCallback.setPassword(newPassword);

            getRealmService().getIdentityStore().updateUserCredentials(uufUser.getUserId(),
                    Collections.singletonList(passwordCallback));
        } catch (IdentityStoreException e) {
            String error = "Failed to update user password.";
            LOGGER.error(error, e);
            throw new UserPortalUIException(error);
        }

    }

    @Override
    public UUFUser addUser(Map<String, String> userClaims, Map<String, String> credentials) throws
            UserPortalUIException {

        UserBean userBean = new UserBean();
        List<Claim> claimsList = new ArrayList<>();
        List<Callback> credentialsList = new ArrayList<>();
        User identityUser;

        for (Map.Entry<String, String> credential : credentials.entrySet()) {
            PasswordCallback passwordCallback = new PasswordCallback("password", false);
            passwordCallback.setPassword(credential.getValue().toCharArray());
            credentialsList.add(passwordCallback);
        }

        for (Map.Entry<String, String> entry : userClaims.entrySet()) {
            Claim claim = new Claim();
            claim.setClaimUri(entry.getKey());
            claim.setValue(entry.getValue());
            claimsList.add(claim);
        }

        userBean.setClaims(claimsList);
        userBean.setCredentials(credentialsList);

        try {
            identityUser = getRealmService().getIdentityStore().addUser(userBean);
        } catch (IdentityStoreException e) {
            String error = "Error while adding user.";
            LOGGER.error(error, e);
            throw new UserPortalUIException(error);
        }
        return new UUFUser(null, identityUser.getUniqueUserId(), identityUser.getDomainName());
    }

    @Override
    public UUFUser addUser(Map<String, String> userClaims, Map<String, String> credentials, String domainName)
            throws UserPortalUIException {

        UserBean userBean = new UserBean();
        List<Claim> claimsList = new ArrayList<>();
        List<Callback> credentialsList = new ArrayList<>();
        User identityUser;

        for (Map.Entry<String, String> credential : credentials.entrySet()) {
            PasswordCallback passwordCallback = new PasswordCallback("password", false);
            passwordCallback.setPassword(credential.getValue().toCharArray());
            credentialsList.add(passwordCallback);
        }

        for (Map.Entry<String, String> entry : userClaims.entrySet()) {
            Claim claim = new Claim();
            claim.setClaimUri(entry.getKey());
            claim.setValue(entry.getValue());
            claimsList.add(claim);
        }

        userBean.setClaims(claimsList);
        userBean.setCredentials(credentialsList);

        try {
            identityUser = getRealmService().getIdentityStore().addUser(userBean, domainName);
        } catch (IdentityStoreException e) {
            String error = "Error while adding user.";
            LOGGER.error(error, e);
            throw new UserPortalUIException(error);
        }
        return new UUFUser(null, identityUser.getUniqueUserId(), identityUser.getDomainName());
    }

    @Override
    public boolean isUserExist(Map<String, String> userClaims, String domain) throws UserPortalUIException {
        List<Claim> claimsList = new ArrayList<>();
        boolean isUserExists;
        for (Map.Entry<String, String> entry : userClaims.entrySet()) {
            Claim claim = new Claim();
            claim.setClaimUri(entry.getKey());
            claim.setValue(entry.getValue());
            claimsList.add(claim);
        }

        try {
            isUserExists = getRealmService().getIdentityStore().isUserExist(claimsList, domain);
        } catch (IdentityStoreException e) {
            String error = "Error while checking whether the user exists.";
            LOGGER.error(error, e);
            throw new UserPortalUIException(error);
        }
        return isUserExists;
    }

    @Override
    public List<String> isUserExist(Map<String, String> userClaims) throws UserPortalUIException {
        List<Claim> claimsList = new ArrayList<>();
        List<String> userExistsMeta;
        for (Map.Entry<String, String> entry : userClaims.entrySet()) {
            Claim claim = new Claim();
            claim.setClaimUri(entry.getKey());
            claim.setValue(entry.getValue());
            claimsList.add(claim);
        }

        try {
            userExistsMeta = getRealmService().getIdentityStore().isUserExist(claimsList);
        } catch (IdentityStoreException e) {
            String error = "Error while checking whether the user exists.";
            LOGGER.error(error, e);
            throw new UserPortalUIException(error);
        }
        return userExistsMeta;
    }

    @Override
    public void updateUserProfile(String uniqueUserId, Map<String, String> updatedClaimsMap) throws
            UserPortalUIException {

        if (updatedClaimsMap == null || updatedClaimsMap.isEmpty()) {
            return;
        }

        List<Claim> updatedClaims = updatedClaimsMap.entrySet().stream()
                .filter(entry -> !StringUtils.isNullOrEmpty(entry.getKey()))
                .map(entry -> new Claim(IdentityMgtConstants.CLAIM_ROOT_DIALECT, entry.getKey(), entry.getValue()))
                .collect(Collectors.toList());

        try {
            getRealmService().getIdentityStore().updateUserClaims(uniqueUserId, updatedClaims, null);
        } catch (IdentityStoreException | UserNotFoundException e) {
            String error = "Failed to updated user profile.";
            LOGGER.error(error, e);
            throw new UserPortalUIException(error);
        }
    }

    @Override
    public List<Claim> getClaimsOfUser(String uniqueUserId, List<MetaClaim> metaClaims) throws UserPortalUIException {
        List<Claim> claimList = null;

        if (StringUtils.isNullOrEmpty(uniqueUserId)) {
            throw new UserPortalUIException("Invalid unique user id.");
        }
        if (metaClaims != null && !metaClaims.isEmpty()) {
            try {
                claimList = getRealmService().getIdentityStore().getClaimsOfUser(uniqueUserId, metaClaims);
            } catch (IdentityStoreException | UserNotFoundException e) {
                String error = "Failed to get claims of the user.";
                LOGGER.error(error, e);
                throw new UserPortalUIException(error);
            }
        } else {
            claimList = Collections.emptyList();
        }
        return claimList;
    }

    @Override
    public Set<String> getDomainNames() throws UserPortalUIException {
        Set<String> domainSet;
        try {
            domainSet = getRealmService().getIdentityStore().getDomainNames();
        } catch (IdentityStoreException e) {
            String error = "Failed to get the domain names.";
            LOGGER.error(error, e);
            throw new UserPortalUIException(error);
        }
        return domainSet;

    }

    @Override
    public String getPrimaryDomainName() throws UserPortalUIException {
        String primaryDomain;
        try {
            primaryDomain = getRealmService().getIdentityStore().getPrimaryDomainName();
        } catch (IdentityStoreException e) {
            String error = "Failed to get the primary domain name.";
            LOGGER.error(error, e);
            throw new UserPortalUIException(error);
        }
        return primaryDomain;

    }

    @Override
    public List<UUFUser> listUsers(String claimUri, String claimValue, int offset, int length,
                                   String domainName) throws UserPortalUIException {

        //TODO check for domain existence when provided
        List<UUFUser> users = new ArrayList<>();
        Claim claim = new Claim();
        claim.setClaimUri(claimUri);
        claim.setValue(claimValue);
        try {
            List<User> userList = getRealmService().getIdentityStore().listUsers(claim, offset, length, domainName);
            for (User user : userList) {
                users.add(new UUFUser("", user.getUniqueUserId(), user.getDomainName()));
            }
        } catch (IdentityStoreException e) {
            String error = "Error while listing users for claimUri :" + claimUri + " and claimValue: " + claimValue;
            LOGGER.error(error, e);
            throw new UserPortalUIException(error);
        }
        return users;
    }

    @Override
    public List<UserListBean> getFilteredList(int offset, int length, String claimURI, String claimValue,
                                              String domainName) throws UserPortalUIException {

        List<UserListBean> userList;
        List<User> users;

        if (length < 0) {
            length = MAX_RECORD_LENGTH;
        }

        if (StringUtils.isNullOrEmpty(domainName)) {
            domainName = getPrimaryDomainName();
        }

        if (StringUtils.isNullOrEmpty(claimURI)
                || StringUtils.isNullOrEmpty(claimValue)) {
            return getUserList(offset, length, domainName);
        } else {
            MetaClaim metaClaim = new MetaClaim();
            metaClaim.setClaimUri(claimURI);
            try {
                users = getRealmService().getIdentityStore().listUsers(
                        metaClaim, claimValue, offset, length, domainName);
            } catch (IdentityStoreException e) {
                String error = "Error while retrieving users for " + claimURI + "= " + claimValue;
                LOGGER.error(error, e);
                throw new UserPortalUIException(error);
            }
        }

        List<MetaClaim> metaClaims = new ArrayList<>();
        MetaClaim metaClaim = new MetaClaim();
        metaClaim.setClaimUri(USERNAME_CLAIM);
        metaClaims.add(metaClaim);

        userList = generateUserListBean(users, metaClaims);

        return userList;
    }

    @Override
    public List<UserListBean> getUserList(int offset, int length, String domainName) throws UserPortalUIException {
        List<User> users;
        if (length < 0) {
            length = MAX_RECORD_LENGTH;
        }
        try {
            users = getRealmService().getIdentityStore().listUsers(offset, length, domainName);
        } catch (IdentityStoreException e) {
            String error = "Error while retrieving users";
            LOGGER.error(error, e);
            throw new UserPortalUIException(error);
        }

        List<MetaClaim> metaClaims = new ArrayList<>();
        MetaClaim metaClaim = new MetaClaim();
        metaClaim.setClaimUri(USERNAME_CLAIM);
        metaClaims.add(metaClaim);

        return generateUserListBean(users, metaClaims);
    }

    private RealmService getRealmService() {
        if (this.realmService == null) {
            throw new IllegalStateException("Realm Service is null.");
        }
        return this.realmService;
    }

    private List<UserListBean> generateUserListBean(List<User> users, List<MetaClaim> userClaims)
            throws UserPortalUIException {
        List<UserListBean> userList = new ArrayList<>();

        List<MetaClaim> groupMetaClaims = new ArrayList<>();
        MetaClaim groupMetaClaim = new MetaClaim();
        groupMetaClaim.setClaimUri(GROUPNAME_CLAIM);
        groupMetaClaims.add(groupMetaClaim);

        for (User user : users) {
            List<Group> groups;
            List<Claim> userId;
            List<Claim> groupId;
            List<String> groupNames = new ArrayList<>();
            String username = null;
            try {
                groups = user.getGroups();
                for (Group group : groups) {
                    groupId = group.getClaims(groupMetaClaims);
                    if (!groupId.isEmpty()) {
                        groupNames.add(groupId.get(0).getValue());
                    }
                }
                userId = user.getClaims(userClaims);
                if (!userId.isEmpty()) {
                    username = userId.get(0).getValue();
                }
            } catch (IdentityStoreException | GroupNotFoundException | UserNotFoundException e) {
                String error = "Error while retrieving user data for user :  " + user.getUniqueUserId();
                LOGGER.error(error, e);
                throw new UserPortalUIException(error);
            }

            String status = null;
            UserState state = UserState.valueOf(user.getState());
            if (state.isInGroup(UserState.Group.DISABLED)) {
                status = DISABLED_STATE;
            } else if (state.isInGroup(UserState.Group.LOCKED)) {
                status = LOCKED_STATE;
            } else if (state.isInGroup(UserState.Group.UNLOCKED)) {
                status = UNLOCKED_STATE;
            }
            UserListBean listEntry = new UserListBean();
            listEntry.setUserId(username);
            listEntry.setDomainName(user.getDomainName());
            listEntry.setUserUniqueId(user.getUniqueUserId());
            listEntry.setState(status);
            listEntry.setGroups(groupNames);
            userList.add(listEntry);
        }
        return userList;
    }
}
