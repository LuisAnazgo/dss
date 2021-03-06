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
package eu.europa.esig.dss.validation.timestamp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.europa.esig.dss.crl.CRLBinary;
import eu.europa.esig.dss.enumerations.ArchiveTimestampType;
import eu.europa.esig.dss.enumerations.TimestampType;
import eu.europa.esig.dss.enumerations.TimestampedObjectType;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.DSSException;
import eu.europa.esig.dss.model.Digest;
import eu.europa.esig.dss.model.identifier.EncapsulatedRevocationTokenIdentifier;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.model.x509.EncapsulatedCertificateTokenIdentifier;
import eu.europa.esig.dss.spi.x509.CertificatePool;
import eu.europa.esig.dss.spi.x509.CommonCertificateSource;
import eu.europa.esig.dss.spi.x509.revocation.crl.CRLRef;
import eu.europa.esig.dss.spi.x509.revocation.ocsp.OCSPRef;
import eu.europa.esig.dss.spi.x509.revocation.ocsp.OCSPResponseBinary;
import eu.europa.esig.dss.utils.Utils;
import eu.europa.esig.dss.validation.AdvancedSignature;
import eu.europa.esig.dss.validation.CertificateRef;
import eu.europa.esig.dss.validation.DefaultAdvancedSignature;
import eu.europa.esig.dss.validation.ISignatureAttribute;
import eu.europa.esig.dss.validation.ListCRLSource;
import eu.europa.esig.dss.validation.ListOCSPSource;
import eu.europa.esig.dss.validation.SignatureCRLSource;
import eu.europa.esig.dss.validation.SignatureCertificateSource;
import eu.europa.esig.dss.validation.SignatureOCSPSource;
import eu.europa.esig.dss.validation.SignatureProperties;
import eu.europa.esig.dss.validation.scope.SignatureScope;

/**
 * Contains a set of {@link TimestampToken}s found in a {@link DefaultAdvancedSignature} object
 */
@SuppressWarnings("serial")
public abstract class AbstractTimestampSource<SignatureAttribute extends ISignatureAttribute> implements SignatureTimestampSource {

	private static final Logger LOG = LoggerFactory.getLogger(AbstractTimestampSource.class);
	
	/**
	 * Sources obtained from a signature object
	 */
	protected final SignatureCertificateSource signatureCertificateSource;
	protected final SignatureCRLSource signatureCRLSource;
	protected final SignatureOCSPSource signatureOCSPSource;
	
	protected final String signatureId;
	protected final transient List<SignatureScope> signatureScopes;
	
	protected CertificatePool certificatePool;
	
	/**
	 * Revocation sources containing merged data from timestamps
	 */
	protected ListCRLSource crlSource;
	protected ListOCSPSource ocspSource;
	
	/**
	 * Local timestamp source to store certificate values found in timestamps
	 */
	protected CommonCertificateSource timestampCertificateSource = new CommonCertificateSource();
	
	// Map between timestamps and found certificates
	private Map<String, List<CertificateToken>> certificateMap;

	// Enclosed content timestamps.
	private List<TimestampToken> contentTimestamps;

	// Enclosed signature timestamps.
	private List<TimestampToken> signatureTimestamps;

	// Enclosed SignAndRefs timestamps.
	private List<TimestampToken> sigAndRefsTimestamps;

	// Enclosed RefsOnly timestamps.
	private List<TimestampToken> refsOnlyTimestamps;

	// This variable contains the list of enclosed archive signature timestamps.
	private List<TimestampToken> archiveTimestamps;

	/**
	 * Default constructor
	 * 
	 * @param signature {@link AdvancedSignature} is being validated
	 */
	protected AbstractTimestampSource(final AdvancedSignature signature) {
		this.signatureCertificateSource = signature.getCertificateSource();
		this.signatureCRLSource = signature.getCRLSource();
		this.signatureOCSPSource = signature.getOCSPSource();
		this.signatureId = signature.getId();
		this.signatureScopes = signature.getSignatureScopes();
	}
	
	@Override
	public List<TimestampToken> getContentTimestamps() {
		if (contentTimestamps == null) {
			createAndValidate();
		}
		return contentTimestamps;
	}
	
	@Override
	public List<TimestampToken> getSignatureTimestamps() {
		if (signatureTimestamps == null) {
			createAndValidate();
		}
		return signatureTimestamps;
	}
	
	@Override
	public List<TimestampToken> getTimestampsX1() {
		if (sigAndRefsTimestamps == null) {
			createAndValidate();
		}
		return sigAndRefsTimestamps;
	}
	
	@Override
	public List<TimestampToken> getTimestampsX2() {
		if (refsOnlyTimestamps == null) {
			createAndValidate();
		}
		return refsOnlyTimestamps;
	}
	
	@Override
	public List<TimestampToken> getArchiveTimestamps() {
		if (archiveTimestamps == null) {
			createAndValidate();
		}
		return archiveTimestamps;
	}
	
	@Override
	public List<TimestampToken> getDocumentTimestamps() {
		/** Applicable only for PAdES */
		return Collections.emptyList();
	}
	
	@Override
	public List<TimestampToken> getAllTimestamps() {
		List<TimestampToken> timestampTokens = new ArrayList<>();
		timestampTokens.addAll(getContentTimestamps());
		timestampTokens.addAll(getSignatureTimestamps());
		timestampTokens.addAll(getTimestampsX1());
		timestampTokens.addAll(getTimestampsX2());
		timestampTokens.addAll(getArchiveTimestamps());
		return timestampTokens;
	}
	
	@Override
	public ListCRLSource getTimestampCRLSources() {
		ListCRLSource result = new ListCRLSource();
		for (TimestampToken timestampToken : getAllTimestamps()) {
			result.add(timestampToken.getCRLSource());
		}
		return result;
	}

	@Override
	public ListOCSPSource getTimestampOCSPSources() {
		ListOCSPSource result = new ListOCSPSource();
		for (TimestampToken timestampToken : getAllTimestamps()) {
			result.add(timestampToken.getOCSPSource());
		}
		return result;
	}
	
	/**
	 * Creates and validates all timestamps
	 * Must be called only once
	 */
	protected void createAndValidate() {
		makeTimestampTokens();
		validateTimestamps();
	}

	@Override
	public void addExternalTimestamp(TimestampToken timestamp) {
		// if timestamp tokens not created yet
		if (archiveTimestamps == null) {
			createAndValidate();
		}
		processExternalTimestamp(timestamp);
		if (TimestampType.ARCHIVE_TIMESTAMP == timestamp.getTimeStampType()) {
			archiveTimestamps.add(timestamp);
		} else {
			throw new DSSException(
					String.format("The signature timestamp source does not support timestamp tokens with type [%s]. " + "The TimestampToken was not added.",
							timestamp.getTimeStampType().name()));
		}
	}
	
	/**
	 * Populates all the lists by data found into the signature
	 */
	protected void makeTimestampTokens() {
		
		// initialize timestamp lists
		contentTimestamps = new ArrayList<>();
		signatureTimestamps = new ArrayList<>();
		sigAndRefsTimestamps = new ArrayList<>();
		refsOnlyTimestamps = new ArrayList<>();
		archiveTimestamps = new ArrayList<>();
		
		// initialize combined revocation sources
		crlSource = new ListCRLSource(signatureCRLSource);
		ocspSource = new ListOCSPSource(signatureOCSPSource);
		
		final SignatureProperties<SignatureAttribute> signedSignatureProperties = getSignedSignatureProperties();
		
		final List<SignatureAttribute> signedAttributes = signedSignatureProperties.getAttributes();
		for (SignatureAttribute signedAttribute : signedAttributes) {
			
			TimestampToken timestampToken;
			
			if (isContentTimestamp(signedAttribute)) {
				timestampToken = makeTimestampToken(signedAttribute, TimestampType.CONTENT_TIMESTAMP, getAllContentTimestampReferences());
				if (timestampToken == null) {
					continue;
				}
				
			} else if (isAllDataObjectsTimestamp(signedAttribute)) {
				timestampToken = makeTimestampToken(signedAttribute, TimestampType.ALL_DATA_OBJECTS_TIMESTAMP, getAllContentTimestampReferences());
				if (timestampToken == null) {
					continue;
				}
				
			} else if (isIndividualDataObjectsTimestamp(signedAttribute)) {				
				List<TimestampedReference> references = getIndividualContentTimestampedReferences(signedAttribute);
				timestampToken = makeTimestampToken(signedAttribute, TimestampType.INDIVIDUAL_DATA_OBJECTS_TIMESTAMP, references);
				if (timestampToken == null) {
					continue;
				}
				
			} else {
				continue;
				
			}
			populateTimestampCertificateSource(timestampToken.getCertificates());
			crlSource.add(timestampToken.getCRLSource());
			ocspSource.add(timestampToken.getOCSPSource());
			contentTimestamps.add(timestampToken);
		}
		
		
		final SignatureProperties<SignatureAttribute> unsignedSignatureProperties = getUnsignedSignatureProperties();
		if (!unsignedSignatureProperties.isExist()) {
			// timestamp tokens cannot be created if signature does not contain "unsigned-signature-properties" element
			return;
		}
		
		final List<TimestampToken> timestamps = new ArrayList<>();
		final List<TimestampedReference> encapsulatedReferences = new ArrayList<>();
		
		final List<SignatureAttribute> unsignedAttributes = unsignedSignatureProperties.getAttributes();
		for (SignatureAttribute unsignedAttribute : unsignedAttributes) {
			
			TimestampToken timestampToken;
			
			if (isSignatureTimestamp(unsignedAttribute)) {
				timestampToken = makeTimestampToken(unsignedAttribute, TimestampType.SIGNATURE_TIMESTAMP, getSignatureTimestampReferences());
				if (timestampToken == null) {
					continue;
				}
				signatureTimestamps.add(timestampToken);
				
			} else if (isCompleteCertificateRef(unsignedAttribute)) {
				addReferences(encapsulatedReferences, getTimestampedCertificateRefs(unsignedAttribute));
				continue;
				
			} else if (isAttributeCertificateRef(unsignedAttribute)) {
				addReferences(encapsulatedReferences, getTimestampedCertificateRefs(unsignedAttribute));
				continue;
				
			} else if (isCompleteRevocationRef(unsignedAttribute)) {
				addReferences(encapsulatedReferences, getTimestampedRevocationRefs(unsignedAttribute));
				continue;
				
			} else if (isAttributeRevocationRef(unsignedAttribute)) {
				addReferences(encapsulatedReferences, getTimestampedRevocationRefs(unsignedAttribute));
				continue;
				
			} else if (isRefsOnlyTimestamp(unsignedAttribute)) {
				timestampToken = makeTimestampToken(unsignedAttribute, TimestampType.VALIDATION_DATA_REFSONLY_TIMESTAMP, encapsulatedReferences);
				if (timestampToken == null) {
					continue;
				}
				refsOnlyTimestamps.add(timestampToken);
				
			} else if (isSigAndRefsTimestamp(unsignedAttribute)) {
				final List<TimestampedReference> references = new ArrayList<>();
				addReferencesForPreviousTimestamps(references, filterSignatureTimestamps(timestamps));
				addReferences(references, encapsulatedReferences);
				
				timestampToken = makeTimestampToken(unsignedAttribute, TimestampType.VALIDATION_DATA_TIMESTAMP, references);
				if (timestampToken == null) {
					continue;
				}
				sigAndRefsTimestamps.add(timestampToken);
				
			} else if (isCertificateValues(unsignedAttribute)) {
				addReferences(encapsulatedReferences, getTimestampedCertificateValues(unsignedAttribute));
				continue;
				
			} else if (isRevocationValues(unsignedAttribute)) {
				addReferences(encapsulatedReferences, getTimestampedRevocationValues(unsignedAttribute));
				continue;
				
			} else if (isArchiveTimestamp(unsignedAttribute)) {
				final List<TimestampedReference> references = new ArrayList<>();
				addReferencesForPreviousTimestamps(references, timestamps);
				addReferences(references, encapsulatedReferences);
				
				timestampToken = makeTimestampToken(unsignedAttribute, TimestampType.ARCHIVE_TIMESTAMP, references);
				if (timestampToken == null) {
					continue;
				}
				timestampToken.setArchiveTimestampType(getArchiveTimestampType(unsignedAttribute));
				addReferences(timestampToken.getTimestampedReferences(), getSignedDataReferences(timestampToken));
				
				archiveTimestamps.add(timestampToken);
				
			} else if (isTimeStampValidationData(unsignedAttribute)) {
				addReferences(encapsulatedReferences, getTimestampValidationData(unsignedAttribute));
				continue;
				
			} else {
				LOG.warn("The unsigned attribute with name [{}] is not supported", unsignedAttribute);
				continue;
			}
			
			populateTimestampCertificateSource(timestampToken.getCertificates());
			crlSource.add(timestampToken.getCRLSource());
			ocspSource.add(timestampToken.getOCSPSource());
			timestamps.add(timestampToken);
			
		}
		
	}
	
	/**
	 * Returns the 'signed-signature-properties' element of the signature
	 * @return {@link SignatureProperties}
	 */
	protected abstract SignatureProperties<SignatureAttribute> getSignedSignatureProperties();
	
	/**
	 * Returns the 'unsigned-signature-properties' element of the signature
	 * @return {@link SignatureProperties}
	 */
	protected abstract SignatureProperties<SignatureAttribute> getUnsignedSignatureProperties();

	/**
	 * Determines if the given {@code signedAttribute} is an instance of "content-timestamp" element
	 * NOTE: Applicable only for CAdES
	 * @param signedAttribute {@link ISignatureAttribute} to process
	 * @return TRUE if the {@code unsignedAttribute} is a Data Objects Timestamp, FALSE otherwise
	 */
	protected abstract boolean isContentTimestamp(SignatureAttribute signedAttribute);
	
	/**
	 * Determines if the given {@code signedAttribute} is an instance of "data-objects-timestamp" element
	 * NOTE: Applicable only for XAdES
	 * @param signedAttribute {@link ISignatureAttribute} to process
	 * @return TRUE if the {@code unsignedAttribute} is a Data Objects Timestamp, FALSE otherwise
	 */
	protected abstract boolean isAllDataObjectsTimestamp(SignatureAttribute signedAttribute);
	
	/**
	 * Determines if the given {@code signedAttribute} is an instance of "individual-data-objects-timestamp" element
	 * NOTE: Applicable only for XAdES
	 * @param signedAttribute {@link ISignatureAttribute} to process
	 * @return TRUE if the {@code unsignedAttribute} is a Data Objects Timestamp, FALSE otherwise
	 */
	protected abstract boolean isIndividualDataObjectsTimestamp(SignatureAttribute signedAttribute);
	
	/**
	 * Determines if the given {@code unsignedAttribute} is an instance of "signature-timestamp" element
	 * @param unsignedAttribute {@link ISignatureAttribute} to process
	 * @return TRUE if the {@code unsignedAttribute} is a Signature Timestamp, FALSE otherwise
	 */
	protected abstract boolean isSignatureTimestamp(SignatureAttribute unsignedAttribute);
	
	/**
	 * Determines if the given {@code unsignedAttribute} is an instance of "complete-certificate-ref" element
	 * @param unsignedAttribute {@link ISignatureAttribute} to process
	 * @return TRUE if the {@code unsignedAttribute} is a Complete Certificate Ref, FALSE otherwise
	 */
	protected abstract boolean isCompleteCertificateRef(SignatureAttribute unsignedAttribute);
	
	/**
	 * Determines if the given {@code unsignedAttribute} is an instance of "attribute-certificate-ref" element
	 * @param unsignedAttribute {@link ISignatureAttribute} to process
	 * @return TRUE if the {@code unsignedAttribute} is an Attribute Certificate Ref, FALSE otherwise
	 */
	protected abstract boolean isAttributeCertificateRef(SignatureAttribute unsignedAttribute);
	
	/**
	 * Determines if the given {@code unsignedAttribute} is an instance of "complete-revocation-ref" element
	 * @param unsignedAttribute {@link ISignatureAttribute} to process
	 * @return TRUE if the {@code unsignedAttribute} is a Complete Revocation Ref, FALSE otherwise
	 */
	protected abstract boolean isCompleteRevocationRef(SignatureAttribute unsignedAttribute);
	
	/**
	 * Determines if the given {@code unsignedAttribute} is an instance of "attribute-revocation-ref" element
	 * @param unsignedAttribute {@link ISignatureAttribute} to process
	 * @return TRUE if the {@code unsignedAttribute} is an Attribute Revocation Ref, FALSE otherwise
	 */
	protected abstract boolean isAttributeRevocationRef(SignatureAttribute unsignedAttribute);
	
	/**
	 * Determines if the given {@code unsignedAttribute} is an instance of "refs-only-timestamp" element
	 * @param unsignedAttribute {@link ISignatureAttribute} to process
	 * @return TRUE if the {@code unsignedAttribute} is a Refs Only TimeStamp, FALSE otherwise
	 */
	protected abstract boolean isRefsOnlyTimestamp(SignatureAttribute unsignedAttribute);
	
	/**
	 * Determines if the given {@code unsignedAttribute} is an instance of "sig-and-refs-timestamp" element
	 * @param unsignedAttribute {@link ISignatureAttribute} to process
	 * @return TRUE if the {@code unsignedAttribute} is a Sig And Refs TimeStamp, FALSE otherwise
	 */
	protected abstract boolean isSigAndRefsTimestamp(SignatureAttribute unsignedAttribute);
	
	/**
	 * Determines if the given {@code unsignedAttribute} is an instance of "certificate-values" element
	 * @param unsignedAttribute {@link ISignatureAttribute} to process
	 * @return TRUE if the {@code unsignedAttribute} is a Certificate Values, FALSE otherwise
	 */
	protected abstract boolean isCertificateValues(SignatureAttribute unsignedAttribute);
	
	/**
	 * Determines if the given {@code unsignedAttribute} is an instance of "revocation-values" element
	 * @param unsignedAttribute {@link ISignatureAttribute} to process
	 * @return TRUE if the {@code unsignedAttribute} is a Revocation Values, FALSE otherwise
	 */
	protected abstract boolean isRevocationValues(SignatureAttribute unsignedAttribute);
	
	/**
	 * Determines if the given {@code unsignedAttribute} is an instance of "archive-timestamp" element
	 * @param unsignedAttribute {@link ISignatureAttribute} to process
	 * @return TRUE if the {@code unsignedAttribute} is an Archive TimeStamp, FALSE otherwise
	 */
	protected abstract boolean isArchiveTimestamp(SignatureAttribute unsignedAttribute);
	
	/**
	 * Determines if the given {@code unsignedAttribute} is an instance of "timestamp-validation-data" element
	 * @param unsignedAttribute {@link ISignatureAttribute} to process
	 * @return TRUE if the {@code unsignedAttribute} is a TimeStamp Validation Data, FALSE otherwise
	 */
	protected abstract boolean isTimeStampValidationData(SignatureAttribute unsignedAttribute);
	
	/**
	 * Creates a timestamp token from the provided {@code signatureAttribute}
	 * @param signatureAttribute {@link ISignatureAttribute} to create timestamp from
	 * @param timestampType a target {@link TimestampType}
	 * @param references list of {@link TimestampedReference}s covered by the current timestamp
	 * @return {@link TimestampToken}
	 */
	protected abstract TimestampToken makeTimestampToken(SignatureAttribute signatureAttribute, TimestampType timestampType,
			List<TimestampedReference> references);
	
	/**
	 * Returns a list of {@link TimestampedReference}s obtained from the {@code signatureScopes}
	 * @return list of {@link TimestampedReference}s
	 */
	protected List<TimestampedReference> getAllContentTimestampReferences() {
		final List<TimestampedReference> references = new ArrayList<>();
		if (Utils.isCollectionNotEmpty(signatureScopes)) {
			for (SignatureScope signatureScope : signatureScopes) {
				addReference(references, new TimestampedReference(signatureScope.getDSSIdAsString(), TimestampedObjectType.SIGNED_DATA));
			}
		}
		return references;
	}
	
	/**
	 * Returns a list of {@link TimestampedReference}s for an "individual-data-objects-timestamp"
	 * NOTE: Used only in XAdES
	 * @param signedAttribute {@link SignatureAttribute}
	 * @return a list of {@link TimestampedReference}s
	 */
	protected abstract List<TimestampedReference> getIndividualContentTimestampedReferences(SignatureAttribute signedAttribute);
	
	/**
	 * Returns a list of {@link TimestampedReference} for a "signature-timestamp" element
	 * @return list of {@link TimestampedReference}s
	 */
	protected List<TimestampedReference> getSignatureTimestampReferences() {
		final List<TimestampedReference> references = new ArrayList<>();
		addReferences(references, getAllContentTimestampReferences());
		addReference(references, new TimestampedReference(signatureId, TimestampedObjectType.SIGNATURE));
		addReferences(references, getSigningCertificateTimestampReferences());
		return references;
	}

	/**
	 * Returns a list of {@code TimestampedReference}s created from signing certificates of the signature
	 * @return list of {@link TimestampedReference}s
	 */
	protected List<TimestampedReference> getSigningCertificateTimestampReferences() {
		return createReferencesForCertificates(signatureCertificateSource.getSigningCertificates());
	}
	
	/**
	 * Creates a list of {@code TimestampedReference}s for the provided list of {@code certificates}
	 * @param certificates collection of {@link CertificateToken}s
	 * @return list of {@link TimestampedReference}s
	 */
	protected List<TimestampedReference> createReferencesForCertificates(Collection<CertificateToken> certificates) {
		final List<TimestampedReference> references = new ArrayList<>();
		for (CertificateToken certificateToken : certificates) {
			addReference(references, new TimestampedReference(certificateToken.getDSSIdAsString(), TimestampedObjectType.CERTIFICATE));
		}
		return references;
	}
	
	/**
	 * Returns a list of {@link TimestampedReference} certificate refs found in the given {@code unsignedAttribute}
	 * @param unsignedAttribute {@link SignatureAttribute} to find references from
	 * @return list of {@link TimestampedReference}s
	 */
	protected List<TimestampedReference> getTimestampedCertificateRefs(SignatureAttribute unsignedAttribute) {
		List<TimestampedReference> timestampedReferences = new ArrayList<>();
		for (Digest certDigest : getCertificateRefDigests(unsignedAttribute)) {
			// firstly try to find into the signature certificate source
			CertificateToken certificate = signatureCertificateSource.getCertificateTokenByDigest(certDigest);
			// if not found, try to access the certificate from timestamp certificate source
			if (certificate == null) {
				certificate = timestampCertificateSource.getCertificateTokenByDigest(certDigest);
			}
			if (certificate != null) {
				timestampedReferences.add(new TimestampedReference(certificate.getDSSIdAsString(), TimestampedObjectType.CERTIFICATE));
			} else {
				// in case if no original Certificate value is present
				CertificateRef certificateRef = signatureCertificateSource.getCertificateRefByDigest(certDigest);
				if (certificateRef != null) {
					timestampedReferences.add(new TimestampedReference(certificateRef.getDSSIdAsString(), TimestampedObjectType.CERTIFICATE));
				}
			}
		}
		return timestampedReferences;
	}
	
	/**
	 * Returns a list of {@link Digest}s from the given {@code unsignedAttribute}
	 * @param unsignedAttribute {@link SignatureAttribute} to get certRef Digests from
	 * @return list of {@link Digest}s
	 */
	protected abstract List<Digest> getCertificateRefDigests(SignatureAttribute unsignedAttribute);
	
	/**
	 * Returns a list of {@link TimestampedReference} revocation refs found in the given {@code unsignedAttribute}
	 * @param unsignedAttribute {@link SignatureAttribute} to find references from
	 * @return list of {@link TimestampedReference}s
	 */
	protected List<TimestampedReference> getTimestampedRevocationRefs(SignatureAttribute unsignedAttribute) {
		List<TimestampedReference> timestampedReferences = new ArrayList<>();
		for (Digest refDigest : getRevocationRefCRLDigests(unsignedAttribute)) {
			CRLBinary identifier = crlSource.getIdentifier(refDigest);
			if (identifier != null) {
				timestampedReferences.add(new TimestampedReference(identifier.asXmlId(), TimestampedObjectType.REVOCATION));
			} else {
				// in case if no original CRL value is present
				CRLRef crlRef = crlSource.getCRLRefByDigest(refDigest);
				if (crlRef != null) {
					timestampedReferences.add(new TimestampedReference(crlRef.getDSSIdAsString(), TimestampedObjectType.REVOCATION));
				}
			}
		}
		
		for (Digest refDigest : getRevocationRefOCSPDigests(unsignedAttribute)) {
			OCSPResponseBinary identifier = ocspSource.getIdentifier(refDigest);
			if (identifier != null) {
				timestampedReferences.add(new TimestampedReference(identifier.asXmlId(), TimestampedObjectType.REVOCATION));
			} else {
				// in case if no original OCSP value is present
				OCSPRef ocspRef = ocspSource.getOCSPRefByDigest(refDigest);
				if (ocspRef != null) {
					timestampedReferences.add(new TimestampedReference(ocspRef.getDSSIdAsString(), TimestampedObjectType.REVOCATION));
				}
			}
		}
		return timestampedReferences;
	}
	
	/**
	 * Returns a list of CRL revocation ref {@link Digest}s from the given {@code unsignedAttribute}
	 * @param unsignedAttribute {@link SignatureAttribute} to get CRLRef Digests from
	 * @return list of {@link Digest}s
	 */
	protected abstract List<Digest> getRevocationRefCRLDigests(SignatureAttribute unsignedAttribute);
	
	/**
	 * Returns a list of OCSP revocation ref {@link Digest}s from the given {@code unsignedAttribute}
	 * @param unsignedAttribute {@link SignatureAttribute} to get OCSPRef Digests from
	 * @return list of {@link Digest}s
	 */
	protected abstract List<Digest> getRevocationRefOCSPDigests(SignatureAttribute unsignedAttribute);
	
	protected List<TimestampedReference> getTimestampedCertificateValues(SignatureAttribute unsignedAttribute) {
		List<TimestampedReference> timestampedReferences = new ArrayList<>();
		for (EncapsulatedCertificateTokenIdentifier certificateIdentifier : getEncapsulatedCertificateIdentifiers(unsignedAttribute)) {
			timestampedReferences.add(new TimestampedReference(certificateIdentifier.asXmlId(), TimestampedObjectType.CERTIFICATE));
		}
		return timestampedReferences;
	}
	
	/**
	 * Returns a list of {@link EncapsulatedCertificateTokenIdentifier}s obtained from the given {@code unsignedAttribute}
	 * @param unsignedAttribute {@link SignatureAttribute} to get certificate identifiers from
	 * @return list of {@link EncapsulatedCertificateTokenIdentifier}s
	 */
	protected abstract List<EncapsulatedCertificateTokenIdentifier> getEncapsulatedCertificateIdentifiers(SignatureAttribute unsignedAttribute);
	
	protected List<TimestampedReference> getTimestampedRevocationValues(SignatureAttribute unsignedAttribute) {
		List<TimestampedReference> timestampedReferences = new ArrayList<>();
		for (EncapsulatedRevocationTokenIdentifier revocationIdentifier : getEncapsulatedCRLIdentifiers(unsignedAttribute)) {
			timestampedReferences.add(new TimestampedReference(revocationIdentifier.asXmlId(), TimestampedObjectType.REVOCATION));
		}
		for (EncapsulatedRevocationTokenIdentifier revocationIdentifier : getEncapsulatedOCSPIdentifiers(unsignedAttribute)) {
			timestampedReferences.add(new TimestampedReference(revocationIdentifier.asXmlId(), TimestampedObjectType.REVOCATION));
		}
		return timestampedReferences;
	}
	
	/**
	 * Returns a list of {@link CRLBinary}s obtained from the given {@code unsignedAttribute}
	 * @param unsignedAttribute {@link SignatureAttribute} to get CRL identifiers from
	 * @return list of {@link CRLBinary}s
	 */
	protected abstract List<CRLBinary> getEncapsulatedCRLIdentifiers(SignatureAttribute unsignedAttribute);
	
	/**
	 * Returns a list of {@link OCSPResponseBinary}s obtained from the given {@code unsignedAttribute}
	 * @param unsignedAttribute {@link SignatureAttribute} to get OCSP identifiers from
	 * @return list of {@link OCSPResponseBinary}s
	 */
	protected abstract List<OCSPResponseBinary> getEncapsulatedOCSPIdentifiers(SignatureAttribute unsignedAttribute);
	
	/**
	 * Returns a list of {@code TimestampedReference}s for the given {@code timestampToken} 
	 * found into signed properties of the signature
	 * NOTE: used only in CAdES. Needs {@code timestampToken} to be initialized before
	 * 
	 * @param timestampToken {@link TimestampToken} to get SignedData references for
	 * @return list of {@link TimestampedReference}s
	 */
	protected List<TimestampedReference> getSignedDataReferences(TimestampToken timestampToken) {
		// empty by default
		return new ArrayList<>();
	}

	/**
	 * Returns a list of all {@code TimestampedReference}s found into CMS SignedData of the signature
	 * NOTE: used only in ASiC-E CAdES
	 * 
	 * @return list of {@link TimestampedReference}s
	 */
	protected List<TimestampedReference> getSignatureSignedDataReferences() {
		// empty by default
		return new ArrayList<>();
	}
	
	/**
	 * Returns a list of {@link TimestampedReference}s encapsulated to the "timestamp-validation-data" {@code unsignedAttribute}
	 * @param unsignedAttribute {@link SignatureAttribute} to get timestamped references from
	 * @return list of {@link TimestampedReference}s
	 */
	protected List<TimestampedReference> getTimestampValidationData(SignatureAttribute unsignedAttribute) {
		List<TimestampedReference> timestampedReferences = new ArrayList<>();
		for (EncapsulatedCertificateTokenIdentifier certificateIdentifier : getEncapsulatedCertificateIdentifiers(unsignedAttribute)) {
			timestampedReferences.add(new TimestampedReference(certificateIdentifier.asXmlId(), TimestampedObjectType.CERTIFICATE));
		}
		for (EncapsulatedRevocationTokenIdentifier crlIdentifier : getEncapsulatedCRLIdentifiers(unsignedAttribute)) {
			timestampedReferences.add(new TimestampedReference(crlIdentifier.asXmlId(), TimestampedObjectType.REVOCATION));
		}
		for (EncapsulatedRevocationTokenIdentifier ocspIdentifier : getEncapsulatedOCSPIdentifiers(unsignedAttribute)) {
			timestampedReferences.add(new TimestampedReference(ocspIdentifier.asXmlId(), TimestampedObjectType.REVOCATION));
		}
		return timestampedReferences;
	}
	
	/**
	 * Adds {@code referenceToAdd} to {@code referenceList} without duplicates
	 * @param referenceList - list of {@link TimestampedReference}s to be extended
	 * @param referenceToAdd - {@link TimestampedReference} to be added
	 */
	protected void addReference(List<TimestampedReference> referenceList, TimestampedReference referenceToAdd) {
		addReferences(referenceList, Arrays.asList(referenceToAdd));
	}
	
	/**
	 * Adds {@code referencesToAdd} to {@code referenceList} without duplicates
	 * @param referenceList - list of {@link TimestampedReference}s to be extended
	 * @param referencesToAdd - {@link TimestampedReference}s to be added
	 */
	protected void addReferences(List<TimestampedReference> referenceList, List<TimestampedReference> referencesToAdd) {
		for (TimestampedReference reference : referencesToAdd) {
			if (!referenceList.contains(reference)) {
				referenceList.add(reference);
			}
		}
	}

	private List<TimestampToken> filterSignatureTimestamps(List<TimestampToken> previousTimestampedTimestamp) {
		List<TimestampToken> result = new ArrayList<>();
		for (TimestampToken timestampToken : previousTimestampedTimestamp) {
			if (TimestampType.SIGNATURE_TIMESTAMP.equals(timestampToken.getTimeStampType())) {
				result.add(timestampToken);
			}
		}
		return result;
	}

	protected void addReferencesForPreviousTimestamps(List<TimestampedReference> references, List<TimestampToken> timestampedTimestamps) {
		for (final TimestampToken timestampToken : timestampedTimestamps) {
			addReference(references, new TimestampedReference(timestampToken.getDSSIdAsString(), TimestampedObjectType.TIMESTAMP));
			addTimestampedReferences(references, timestampToken);
			addEncapsulatedValuesFromTimestamp(references, timestampToken);
		}
	}
	
	private void addTimestampedReferences(List<TimestampedReference> references, TimestampToken timestampedTimestamp) {
		for (TimestampedReference timestampedReference : timestampedTimestamp.getTimestampedReferences()) {
			addReference(references, timestampedReference);
		}
	}
	
	/**
	 * Adds to the {@code references} list all validation data embedded to the {@code timestampedTimestamp}
	 * @param references list of {@link TimestampedReference}s to extend
	 * @param timestampedTimestamp {@link TimestampToken} to extract embedded values from
	 */
	protected void addEncapsulatedValuesFromTimestamp(List<TimestampedReference> references, TimestampToken timestampedTimestamp) {
		List<CertificateToken> certificates = timestampedTimestamp.getCertificates();
		for (final CertificateToken certificate : certificates) {
			addReference(references, new TimestampedReference(certificate.getDSSIdAsString(), TimestampedObjectType.CERTIFICATE));
		}
	}
	
	/**
	 * Returns {@link ArchiveTimestampType} for the given {@code unsignedAttribute}
	 * @param unsignedAttribute {@link SignatureAttribute} to get archive timestamp type for
	 */
	protected abstract ArchiveTimestampType getArchiveTimestampType(SignatureAttribute unsignedAttribute);
	
	/**
	 * Validates list of all timestamps present in the source
	 */
	protected void validateTimestamps() {
		
		TimestampDataBuilder timestampDataBuilder = getTimestampDataBuilder();

		/*
		 * This validates the content-timestamp tokensToProcess present in the signature.
		 */
		for (final TimestampToken timestampToken : getContentTimestamps()) {
			final DSSDocument timestampedData = timestampDataBuilder.getContentTimestampData(timestampToken);
			timestampToken.matchData(timestampedData);
		}

		/*
		 * This validates the signature timestamp tokensToProcess present in the signature.
		 */
		for (final TimestampToken timestampToken : getSignatureTimestamps()) {
			final DSSDocument timestampedData = timestampDataBuilder.getSignatureTimestampData(timestampToken);
			timestampToken.matchData(timestampedData);
		}

		/*
		 * This validates the SigAndRefs timestamp tokensToProcess present in the signature.
		 */
		for (final TimestampToken timestampToken : getTimestampsX1()) {
			final DSSDocument timestampedData = timestampDataBuilder.getTimestampX1Data(timestampToken);
			timestampToken.matchData(timestampedData);
		}

		/*
		 * This validates the RefsOnly timestamp tokensToProcess present in the signature.
		 */
		for (final TimestampToken timestampToken : getTimestampsX2()) {
			final DSSDocument timestampedData = timestampDataBuilder.getTimestampX2Data(timestampToken);
			timestampToken.matchData(timestampedData);
		}

		/*
		 * This validates the archive timestamp tokensToProcess present in the signature.
		 */
		for (final TimestampToken timestampToken : getArchiveTimestamps()) {
			if (!timestampToken.isProcessed()) {
				final DSSDocument timestampedData = timestampDataBuilder.getArchiveTimestampData(timestampToken);
				timestampToken.matchData(timestampedData);
			}
		}
		
	}
	
	/**
	 * Returns a related {@link TimestampDataBuilder}
	 * @return {@link TimestampDataBuilder}
	 */
	protected abstract TimestampDataBuilder getTimestampDataBuilder();

	@Override
	public Map<String, List<CertificateToken>> getCertificateMapWithinTimestamps(boolean skipLastArchiveTimestamp) {
		if (certificateMap != null) {
			return certificateMap;
		}
		
		certificateMap = new HashMap<>();

		// We can have more than one chain in the signature : signing certificate, ocsp
		// responder, ...
		int timestampCounter = 0;
		for (final TimestampToken timestampToken : getContentTimestamps()) {
			certificateMap.put(timestampToken.getTimeStampType().name() + timestampCounter++, timestampToken.getCertificates());
		}
		for (final TimestampToken timestampToken : getTimestampsX1()) {
			certificateMap.put(timestampToken.getTimeStampType().name() + timestampCounter++, timestampToken.getCertificates());
		}
		for (final TimestampToken timestampToken : getTimestampsX2()) {
			certificateMap.put(timestampToken.getTimeStampType().name() + timestampCounter++, timestampToken.getCertificates());
		}
		for (final TimestampToken timestampToken : getSignatureTimestamps()) {
			certificateMap.put(timestampToken.getTimeStampType().name() + timestampCounter++, timestampToken.getCertificates());
		}

		List<TimestampToken> archiveTsps = getArchiveTimestamps();
		int archiveTimestampsSize = archiveTsps.size();
		if (skipLastArchiveTimestamp && archiveTimestampsSize > 0) {
			archiveTimestampsSize--;
		}
		for (int ii = 0; ii < archiveTimestampsSize; ii++) {
			TimestampToken timestampToken = archiveTsps.get(ii);
			certificateMap.put(timestampToken.getTimeStampType().name() + timestampCounter++, timestampToken.getCertificates());
		}

		return certificateMap;
	}
	
	@Override
	public List<CertificateToken> getCertificates() {
		return timestampCertificateSource.getCertificates();
	}
	
	/**
	 * Adds {@code certificates} to the {@code timestampCertificateSource}
	 * @param certificates list of {@link CertificateToken}s
	 */
	protected void populateTimestampCertificateSource(List<CertificateToken> certificates) {
		for (CertificateToken certificate : certificates) {
			timestampCertificateSource.addCertificate(certificate);
		}
	}
	
	private void processExternalTimestamp(TimestampToken externalTimestamp) {
		// add all validation data present in Signature CMS SignedData, because an external timestamp covers a whole signature file
		addReferences(externalTimestamp.getTimestampedReferences(), getSignatureSignedDataReferences());
		// add references from previously added timestamps
		addReferencesForPreviousTimestamps(externalTimestamp.getTimestampedReferences(), getAllTimestamps());
		// populate timestamp certificate source with values present in the timestamp
		populateTimestampCertificateSource(externalTimestamp.getCertificates());
	}

}
