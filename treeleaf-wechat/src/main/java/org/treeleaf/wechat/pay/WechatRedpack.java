package org.treeleaf.wechat.pay;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.treeleaf.common.bean.FastBeanUtils;
import org.treeleaf.common.http.Http;
import org.treeleaf.common.http.Post;
import org.treeleaf.common.safe.Uuid;
import org.treeleaf.wechat.pay.entity.Redpack;
import org.treeleaf.wechat.pay.entity.RedpackResult;

import javax.net.ssl.SSLContext;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Map;

/**
 * 微信红包接口
 * <p>
 * Created by leaf on 2016/3/17 0017.
 */
public class WechatRedpack extends WechatMerchantInterface {

    private static Logger log = LoggerFactory.getLogger(WechatRedpack.class);

    /**
     * 应用id
     */
    private String appid;

    /**
     * 微信支付商户号
     */
    private String merchantNo;

    /**
     * 商户密钥
     */
    private String key;

    private String certPath;

    /**
     * 发送微信定额红包
     *
     * @param redpack
     */
    public RedpackResult send(Redpack redpack) {
        redpack.setWxappid(this.appid);
        redpack.setMch_id(this.merchantNo);
        redpack.setNonce_str(Uuid.buildBase64UUID());

        String sign = WechatPaySignature.sign(redpack, this.key);
        redpack.setSign(sign);

        Map<String, String> req;
        try {
            req = FastBeanUtils.describe(redpack);
            req.remove("class");
        } catch (Exception e) {
            throw new RuntimeException("将java对象转为Map失败", e);
        }

        //3.转xml
        String xml = this.mapToXml(req);

        log.debug("生成微信统一下单接口参数:\n{}", xml);

        //4.发送
        String r = new Post("https://api.mch.weixin.qq.com/mmpaymkttransfers/sendredpack")
                .header(Http.NAME_CONTENT_TYPE, Http.CONTENT_TYPE_XML)
                .body(xml).post();



        // Allow TLSv1 protocol only
//        SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(
//                sslcontext,
//                new String[] { "TLSv1" },
//                null,
//                SSLConnectionSocketFactory.BROWSER_COMPATIBLE_HOSTNAME_VERIFIER);
//        CloseableHttpClient httpclient = HttpClients.custom()
//                .setSSLSocketFactory(sslsf)
//                .build();
//        try {
//
//            HttpGet httpget = new HttpGet("https://api.mch.weixin.qq.com/secapi/pay/refund");
//
//            System.out.println("executing request" + httpget.getRequestLine());
//
//            CloseableHttpResponse response = httpclient.execute(httpget);
//            try {
//                HttpEntity entity = response.getEntity();
//
//                System.out.println("----------------------------------------");
//                System.out.println(response.getStatusLine());
//                if (entity != null) {
//                    System.out.println("Response content length: " + entity.getContentLength());
//                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(entity.getContent()));
//                    String text;
//                    while ((text = bufferedReader.readLine()) != null) {
//                        System.out.println(text);
//                    }
//
//                }
//                EntityUtils.consume(entity);
//            } finally {
//                response.close();
//            }
//        } finally {
//            httpclient.close();
//        }



        log.debug("调用微信统一下单接口成功,返回:\n{}", r);

        //5.将从API返回的XML数据映射到Java对象
        Map returnMap = this.xmlToMap(r);

        RedpackResult redpackResult = FastBeanUtils.fastPopulate(RedpackResult.class, returnMap);

        //7.验签
        String returnSign = (String) returnMap.get("sign");
        if ("SUCCESS".equals(redpackResult.getReturn_code()) && "SUCCESS".equals(redpackResult.getResult_code())) {
            returnMap.remove("sign");
            String s = WechatPaySignature.sign(returnMap, this.key);
            if (StringUtils.isBlank(returnSign) || !s.equals(returnSign)) {
                throw new WechatPayException("微信发送红包返回数据验签失败:" + returnSign + "," + s);
            }
        }

        return redpackResult;
    }

    private void buildSslContext() {
        InputStream in = null;
        try {
            KeyStore keyStore  = KeyStore.getInstance("PKCS12");
            in = Files.newInputStream(Paths.get(this.certPath));
            keyStore.load(in, "10016225".toCharArray());
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            IOUtils.closeQuietly(in);
        }


//        // Trust own CA and all self-signed certs
//        SSLContext sslcontext = SSLContext.getDefault().
//                .loadKeyMaterial(keyStore, "10016225".toCharArray())
//                .build();

    }

    public void setAppid(String appid) {
        this.appid = appid;
    }

    public void setMerchantNo(String merchantNo) {
        this.merchantNo = merchantNo;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public void setCertPath(String certPath) {
        this.certPath = certPath;
    }
}
