/**
 * DSS - Digital Signature Services
 * Copyright (C) 2015 European Commission, provided under the CEF programme
 * 
 * This file is part of the "DSS - Digital Signature Services" project.
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package eu.europa.esig.dss.cades.extension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import eu.europa.esig.dss.cades.CAdESSignatureParameters;
import eu.europa.esig.dss.cades.signature.CAdESService;
import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.enumerations.SignaturePackaging;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.DSSException;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.test.signature.PKIFactoryAccess;

public class CAdESExtensionAllSelfSignedCertsTest extends PKIFactoryAccess {
	
	private DSSDocument documentToSign;
	private CAdESSignatureParameters parameters;
	private CAdESService service;
	
	@BeforeEach
	public void init() {
		documentToSign = new InMemoryDocument("Hello World!".getBytes());
		
		parameters = new CAdESSignatureParameters();
		parameters.setSignaturePackaging(SignaturePackaging.ENVELOPING);
		parameters.setSigningCertificate(getSigningCert());
		parameters.setCertificateChain(getCertificateChain());
		parameters.setDigestAlgorithm(DigestAlgorithm.SHA256);

        service = new CAdESService(getCompleteCertificateVerifier());
        service.setTspSource(getSelfSignedTsa());
	}

	@Test
	public void bToTTest() {
		parameters.setSignatureLevel(SignatureLevel.CAdES_BASELINE_B);
        DSSDocument signedDocument = sign();
        
		parameters.setSignatureLevel(SignatureLevel.CAdES_BASELINE_T);
		DSSDocument extendedDocument = extend(signedDocument);
		assertNotNull(extendedDocument);
	}

	@Test
	public void bToLTTest() {
		Exception exception = assertThrows(DSSException.class, () -> {
			parameters.setSignatureLevel(SignatureLevel.CAdES_BASELINE_B);
	        DSSDocument signedDocument = sign();
	        
			parameters.setSignatureLevel(SignatureLevel.CAdES_BASELINE_LT);
			extend(signedDocument);
		});
		assertEquals("Cannot extend the signature. The signature contains only self-signed certificate chains!", exception.getMessage());
	}

	@Test
	public void tToLTTest() {
		Exception exception = assertThrows(DSSException.class, () -> {
			parameters.setSignatureLevel(SignatureLevel.CAdES_BASELINE_T);
	        DSSDocument signedDocument = sign();
	        
			parameters.setSignatureLevel(SignatureLevel.CAdES_BASELINE_LT);
			extend(signedDocument);
		});
		assertEquals("Cannot extend the signature. The signature contains only self-signed certificate chains!", exception.getMessage());
	}

	@Test
	public void tToLTATest() {
		Exception exception = assertThrows(DSSException.class, () -> {
			parameters.setSignatureLevel(SignatureLevel.CAdES_BASELINE_T);
	        DSSDocument signedDocument = sign();
	        
			parameters.setSignatureLevel(SignatureLevel.CAdES_BASELINE_LTA);
			extend(signedDocument);
		});
		assertEquals("Cannot extend the signature. The signature contains only self-signed certificate chains!", exception.getMessage());
	}
	
	private DSSDocument sign() {
        ToBeSigned dataToSign = service.getDataToSign(documentToSign, parameters);
        SignatureValue signatureValue = getToken().sign(dataToSign, parameters.getDigestAlgorithm(), getPrivateKeyEntry());
        return service.signDocument(documentToSign, parameters, signatureValue);
	}
	
	private DSSDocument extend(DSSDocument document) {
		return service.extendDocument(document, parameters);
	}

	@Override
	protected String getSigningAlias() {
		return SELF_SIGNED_USER;
	}

}
