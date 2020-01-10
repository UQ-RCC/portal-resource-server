/*
 * RCC Portals Resource Server
 * https://github.com/UQ-RCC/portal-resource-server
 *
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2020 The University of Queensland
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package au.edu.uq.rcc.portal.resource.ssh;

import java.security.KeyPair;
import java.time.Instant;

/**
 * Contains certificate details for SSH auth
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public class CertAuthInfo {
	private final String userName;
	private String certificate;
	private final KeyPair keypair;
	private Instant validAfter;
	private Instant validBefore;

	public CertAuthInfo(String userName, String certificate, KeyPair keypair, Instant validAfter, Instant validBefore) {
		super();
		this.userName = userName;
		this.certificate = certificate;
		this.keypair = keypair;
		this.validAfter = validAfter;
		this.validBefore = validBefore;
	}

	public String getUserName() {
		return userName;
	}

	public String getCertificate() {
		return this.certificate;
	}

	public KeyPair getKeyPair() {
		return this.keypair;
	}

	public boolean hasExpired() {
		return Instant.now().isAfter(validBefore);
	}

	public Instant getValidAfter() {
		return this.validAfter;
	}

	public Instant getValidBefore() {
		return this.validBefore;
	}

}
