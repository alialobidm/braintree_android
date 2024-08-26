package com.braintreepayments.api.threedsecure;

import android.content.Context;

import com.braintreepayments.api.core.BraintreeException;
import com.braintreepayments.api.core.Configuration;
import com.cardinalcommerce.cardinalmobilesdk.Cardinal;
import com.cardinalcommerce.cardinalmobilesdk.enums.CardinalEnvironment;
import com.cardinalcommerce.cardinalmobilesdk.enums.CardinalRenderType;
import com.cardinalcommerce.cardinalmobilesdk.enums.CardinalUiType;
import com.cardinalcommerce.cardinalmobilesdk.models.CardinalChallengeObserver;
import com.cardinalcommerce.cardinalmobilesdk.models.CardinalConfigurationParameters;
import com.cardinalcommerce.cardinalmobilesdk.models.ValidateResponse;
import com.cardinalcommerce.cardinalmobilesdk.services.CardinalInitService;

import org.json.JSONArray;

class CardinalClient {

    private String consumerSessionId;

    CardinalClient() {
    }

    void initialize(Context context, Configuration configuration, final ThreeDSecureRequest request,
                    final CardinalInitializeCallback callback) throws BraintreeException {
        configureCardinal(context, configuration, request);

        CardinalInitService cardinalInitService = new CardinalInitService() {
            @Override
            public void onSetupCompleted(String sessionId) {
                consumerSessionId = sessionId;
                callback.onResult(consumerSessionId, null);
            }

            @Override
            public void onValidated(ValidateResponse validateResponse, String serverJWT) {
                if (consumerSessionId == null) {
                    callback.onResult(null,
                            new BraintreeException("consumer session id not available"));
                } else {
                    callback.onResult(consumerSessionId, null);
                }
            }
        };

        try {
            Cardinal.getInstance()
                    .init(configuration.getCardinalAuthenticationJwt(), cardinalInitService);
        } catch (RuntimeException e) {
            throw new BraintreeException("Cardinal SDK init Error.", e);
        }
    }

    void continueLookup(ThreeDSecureParams threeDSecureParams,
                        CardinalChallengeObserver challengeObserver) throws BraintreeException {
        ThreeDSecureLookup lookup = threeDSecureParams.getLookup();
        String transactionId = lookup.getTransactionId();
        String paReq = lookup.getPareq();
        try {
            Cardinal.getInstance().cca_continue(transactionId, paReq, challengeObserver);
        } catch (RuntimeException e) {
            throw new BraintreeException("Cardinal SDK cca_continue Error.", e);
        }
    }

    private void configureCardinal(Context context, Configuration configuration,
                                   ThreeDSecureRequest request) throws BraintreeException {
        CardinalEnvironment cardinalEnvironment = CardinalEnvironment.STAGING;
        if ("production".equalsIgnoreCase(configuration.getEnvironment())) {
            cardinalEnvironment = CardinalEnvironment.PRODUCTION;
        }

        CardinalConfigurationParameters cardinalConfigurationParameters =
                new CardinalConfigurationParameters();
        cardinalConfigurationParameters.setEnvironment(cardinalEnvironment);
        cardinalConfigurationParameters.setRequestTimeout(8000);
        cardinalConfigurationParameters.setEnableDFSync(true);

        switch (request.getUiType()) {
            case NATIVE: cardinalConfigurationParameters.setUiType(CardinalUiType.NATIVE);
            case HTML: cardinalConfigurationParameters.setUiType(CardinalUiType.HTML);
            case BOTH: cardinalConfigurationParameters.setUiType(CardinalUiType.BOTH);
        }

        if (request.getRenderTypes() != null) {
            JSONArray renderTypes = new JSONArray();

            for (ThreeDSecureRenderType renderType : request.getRenderTypes()) {
                if (renderType.equals(ThreeDSecureRenderType.OTP)) {
                    renderTypes.put(CardinalRenderType.OTP);
                } else if (renderType.equals(ThreeDSecureRenderType.SINGLE_SELECT)) {
                    renderTypes.put(CardinalRenderType.SINGLE_SELECT);
                } else if (renderType.equals(ThreeDSecureRenderType.MULTI_SELECT)) {
                    renderTypes.put(CardinalRenderType.MULTI_SELECT);
                } else if (renderType.equals(ThreeDSecureRenderType.OOB)) {
                    renderTypes.put(CardinalRenderType.OOB);
                } else if (renderType.equals(ThreeDSecureRenderType.RENDER_HTML)) {
                    renderTypes.put(CardinalRenderType.HTML);
                }
            }

            cardinalConfigurationParameters.setRenderType(renderTypes);
        }

        if (request.getV2UiCustomization() != null) {
            cardinalConfigurationParameters.setUICustomization(
                    request.getV2UiCustomization().getCardinalUiCustomization());
        }

        try {
            Cardinal.getInstance().configure(context, cardinalConfigurationParameters);
        } catch (RuntimeException e) {
            throw new BraintreeException("Cardinal SDK configure Error.", e);
        }
    }

    String getConsumerSessionId() {
        return consumerSessionId;
    }

    void cleanup() {
        Cardinal.getInstance().cleanup();
    }
}
