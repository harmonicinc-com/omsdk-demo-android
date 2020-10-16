package com.harmonicinc.omsdkdemo.util;

import android.content.Context;
import android.webkit.WebView;

import androidx.annotation.NonNull;

import com.iab.omid.library.harmonicinc.Omid;
import com.iab.omid.library.harmonicinc.adsession.AdSession;
import com.iab.omid.library.harmonicinc.adsession.AdSessionConfiguration;
import com.iab.omid.library.harmonicinc.adsession.AdSessionContext;
import com.iab.omid.library.harmonicinc.adsession.CreativeType;
import com.iab.omid.library.harmonicinc.adsession.ImpressionType;
import com.iab.omid.library.harmonicinc.adsession.Owner;
import com.iab.omid.library.harmonicinc.adsession.Partner;
import com.iab.omid.library.harmonicinc.adsession.VerificationScriptResource;
import com.harmonicinc.omsdkdemo.BuildConfig;

import org.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.List;

/**
 * AdSessionUtil
 */

public final class AdSessionUtil {

    @NonNull
    public static AdSession getNativeAdSession(Context context, String customReferenceData, CreativeType creativeType, JSONObject verificationDetails) throws MalformedURLException {
        ensureOmidActivated(context);

        AdSessionConfiguration adSessionConfiguration =
                AdSessionConfiguration.createAdSessionConfiguration(creativeType,
                        (creativeType == CreativeType.AUDIO ? ImpressionType.AUDIBLE : ImpressionType.VIEWABLE),
                        Owner.NATIVE,
                        (creativeType == CreativeType.HTML_DISPLAY || creativeType == CreativeType.NATIVE_DISPLAY) ?
                                Owner.NONE : Owner.NATIVE, false);

        Partner partner = Partner.createPartner(BuildConfig.PARTNER_NAME, BuildConfig.VERSION_NAME);
        final String omidJs = OmidJsLoader.getOmidJs(context);
        List<VerificationScriptResource> verificationScripts = AdSessionUtil.getVerificationScriptResources(verificationDetails);
        AdSessionContext adSessionContext = AdSessionContext.createNativeAdSessionContext(partner, omidJs, verificationScripts, null, customReferenceData);
        return AdSession.createAdSession(adSessionConfiguration, adSessionContext);
    }

    @NonNull
    private static List<VerificationScriptResource> getVerificationScriptResources(JSONObject verificationDetails) throws MalformedURLException {
        VerificationScriptResource verificationScriptResource;
        try {
            String vendorKey = verificationDetails.getString("vendorKey");
            URL javascriptResourceUrl = getURL(verificationDetails.getString("javascriptResourceUrl"));
            String verificationParameters = verificationDetails.getString("verificationParameters");
            if(verificationParameters.equals("")){
                verificationScriptResource = VerificationScriptResource.createVerificationScriptResourceWithoutParameters(javascriptResourceUrl);
            } else {
                verificationScriptResource =
                        VerificationScriptResource.createVerificationScriptResourceWithParameters(
                                verificationDetails.getString("vendorKey"),
                                getURL(verificationDetails.getString("javascriptResourceUrl")),
                                verificationDetails.getString("verificationParameters")
                        );
            }
        } catch (Exception ex) {
            verificationScriptResource = VerificationScriptResource.createVerificationScriptResourceWithoutParameters(getDefaultURL());
        }


        return Collections.singletonList(verificationScriptResource);
    }

    private static URL getURL(String scriptUrl) throws MalformedURLException {
        return new URL(scriptUrl);
    }

    private static URL getDefaultURL() throws MalformedURLException {
        return new URL(BuildConfig.VERIFICATION_URL);
    }

    /**
     * Lazily activate the OMID API.
     *
     * @param context any context
     */
    private static void ensureOmidActivated(Context context) {
        Omid.activate(context.getApplicationContext());
    }
}
