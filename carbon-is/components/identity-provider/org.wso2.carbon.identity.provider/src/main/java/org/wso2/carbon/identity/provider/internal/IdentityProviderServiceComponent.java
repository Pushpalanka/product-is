/*
 * Copyright (c) 2016 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.identity.provider.internal;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.identity.provider.listener.IdentityProviderMgtListener;

import java.util.ArrayList;
import java.util.List;

/**
 * @scr.component name="idp.mgt.dscomponent" immediate="true"
 * @scr.reference name="idp.mgt.event.listener.service"
 * interface="org.wso2.carbon.idp.mgt.listener.IdentityProviderMgtListener"
 * cardinality="0..n" policy="dynamic"
 * bind="setIdentityProviderMgtListenerService"
 * unbind="unsetIdentityProviderMgtListenerService"
 */
public class IdentityProviderServiceComponent {

    private static Log log = LogFactory.getLog(IdentityProviderServiceComponent.class);

    private static volatile List<IdentityProviderMgtListener> idpMgtListeners = new ArrayList<>();


    }


