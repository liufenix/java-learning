package com.fenix.java.awss3;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.thread.BlockPolicy;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.SecureUtil;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.alibaba.fastjson.JSON;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


public class S3Operation {


    static Logger log = LoggerFactory.getLogger(S3Operation.class);

    static volatile int count = 0;


    static String ENDPOINT = "https://172.38.30.192:9000";
    static String AK = "";
    static String SK = "";

    static String XSKY_COUNT = "XSKY_COUNT";
    static String ENCODE_COUNT = "ENCODE_COUNT";
    static String ENCODE_KEYS = "ENCODE_KEYS";

    static AmazonS3 awsS3Client = AmazonS3ClientUtil.getAwsS3Client(AK, SK, ENDPOINT);

    public static void main(String[] args) throws IOException {

        File file = new File("D:\\Downloads\\nohup.out");
        long length = file.length();
        log.info("{}", length);


        String s1 = SecureUtil.md5(file);

//        String s = Md5Utils.md5AsBase64(file);
        log.info("md5: {}", s1);

        headObject();
    }


    public static void changeStorageClass() {

        String bucket = "testnbu";

        ListObjectsV2Request listObjectsV2Request = new ListObjectsV2Request();
        listObjectsV2Request.setBucketName(bucket);
        listObjectsV2Request.setMaxKeys(20000);


        ListObjectsV2Result listObjectsV2Result = awsS3Client.listObjectsV2(listObjectsV2Request);
        List<S3ObjectSummary> objectSummaries = listObjectsV2Result.getObjectSummaries();
        int count = 0;
        for (S3ObjectSummary objectSummary : objectSummaries) {
            if (objectSummary.getKey().endsWith("/") || objectSummary.getStorageClass().equals("GLACIER")) {
                continue;
            }
            count++;
            awsS3Client.changeObjectStorageClass(bucket, objectSummary.getKey(), StorageClass.Glacier);
        }
        log.info("{}", count);


    }

    public static void checkObject() {

        String srcBucket = "gam_cur-222-sy20210302";
        String destBucket = "gam-his-222-h";

        String srcPrefix = "sy20210302/";
        String destPrefix = "";

        String continueToken = "";

        int successCount = 0;
        int failCount = 0;
        int copyCount = 0;
        while (true) {
            ListObjectsV2Request listObjectsV2Request = new ListObjectsV2Request();
            listObjectsV2Request.setBucketName(srcBucket);
            listObjectsV2Request.setPrefix(srcPrefix);
            listObjectsV2Request.setContinuationToken(continueToken);
            listObjectsV2Request.setMaxKeys(1000);


            ListObjectsV2Result listObjectsV2Result = awsS3Client.listObjectsV2(listObjectsV2Request);

            List<S3ObjectSummary> objectSummaries = listObjectsV2Result.getObjectSummaries();
            for (S3ObjectSummary objectSummary : objectSummaries) {
                String destKey = destPrefix + objectSummary.getKey();
                ListObjectsV2Result destObjectsV2Result = awsS3Client.listObjectsV2(destBucket, destKey);
                List<S3ObjectSummary> destObjectSummaries = destObjectsV2Result.getObjectSummaries();

                if (destObjectSummaries.size() > 0) {
                    S3ObjectSummary s3ObjectSummary = destObjectSummaries.get(0);
                    if (s3ObjectSummary.getKey().equals(destKey) && objectSummary.getSize() == s3ObjectSummary.getSize() && objectSummary.getETag().equals(s3ObjectSummary.getETag())) {
                        try {
                            awsS3Client.deleteObject(srcBucket, objectSummary.getKey());
                            log.info("bucket={},key={} ????????????????????????????????????", srcBucket, objectSummary.getKey());
                            successCount++;
                        } catch (Exception e) {
                            log.error("{} == ????????????", objectSummary.getKey());
                            log.error("?????? error: ", e);
                            failCount++;
                        }
                    }
                } else {
                    try {
                        awsS3Client.copyObject(srcBucket, objectSummary.getKey(), destBucket, destKey);
                        log.info("???????????? {}", objectSummary.getKey());
                        copyCount++;
                    } catch (Exception e) {
                        log.error("{} == ????????????", objectSummary.getKey());
                        log.error("?????? error: ", e);
                    }
                }
            }

            log.info("????????????{}??? ????????????{}??? ????????????{}", successCount, failCount, copyCount);

            if (StrUtil.isEmpty(listObjectsV2Result.getNextContinuationToken())) {
                break;
            }
            continueToken = listObjectsV2Result.getNextContinuationToken();
        }
    }

    public static void moveData() {

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                12,
                12,
                0L,
                TimeUnit.MICROSECONDS,
                new LinkedBlockingQueue<>(60),
                new BlockPolicy()
        );

        String srcBucket = "gam_currentdata";
        String srcPath = "sy20190415/";
        String destBucket = "gam-his-222-g";
        String destPath = "";


        String startAfter = "sy20190415/07072020/Yang Hai Yan/C2014732/1.3.12.2.1107.5.1.4.80456.30000020070700043710400016926";

//        String srcBucket = "fenix";
//        String srcPath = "sync2/";
//        String destBucket = "fenix";
//        String destPath = "move/";


        String continueToken = "";
        while (true) {
            ListObjectsV2Request listObjectsV2Request = new ListObjectsV2Request();
            listObjectsV2Request.setBucketName(srcBucket);
            listObjectsV2Request.setPrefix(srcPath);
            listObjectsV2Request.setStartAfter(startAfter);
            listObjectsV2Request.setContinuationToken(continueToken);
            listObjectsV2Request.setMaxKeys(100);
            ListObjectsV2Result listObjectsV2Result = null;
            try {
                listObjectsV2Result = awsS3Client.listObjectsV2(listObjectsV2Request);
            } catch (Exception e) {
                log.error("???????????????", e);
                continue;
            }

            List<S3ObjectSummary> objectSummaries = listObjectsV2Result.getObjectSummaries();

            for (S3ObjectSummary objectSummary : objectSummaries) {
                count += 1;

                log.info("{}. ???????????? ??? {}", count, objectSummary.getKey());
                int finalCount = count;
                executor.execute(() -> {
                    final Logger successLog = LoggerFactory.getLogger("successLog");
                    final Logger failLog = LoggerFactory.getLogger("failLog");
                    String destKey = destPath + objectSummary.getKey();
                    try {
                        CopyObjectResult copyObjectResult = awsS3Client.copyObject(srcBucket, objectSummary.getKey(), destBucket, destKey);
                        if (!copyObjectResult.getETag().equals(objectSummary.getETag())) {
                            failLog.info(objectSummary.getKey());
                        } else {
                            successLog.info(objectSummary.getKey());
                        }
                    } catch (Exception e) {
                        log.error("{} ?????? error:", objectSummary.getKey(), e);
                        failLog.info(objectSummary.getKey());
                    }

                    log.info("{}. ???????????? ??? {}", finalCount, objectSummary.getKey());
                });

            }

            if (StrUtil.isEmpty(listObjectsV2Result.getNextContinuationToken())) {
                break;
            }
            continueToken = listObjectsV2Result.getNextContinuationToken();
        }

        executor.shutdown();
        while (!executor.isShutdown()) {
            try {
                log.info("??????????????? {}??? ????????????????????? {}", count, executor.getCompletedTaskCount());
                Thread.sleep(5000L);
            } catch (InterruptedException e) {
                log.error("error: ", e);
            }
        }

    }

    /**
     * ????????????????????????key
     */
    public static void moveSingleObj() {
        String srcBucket = System.getProperty("SRC_BUCKET");
        String destBucket = System.getProperty("DEST_BUCKET");
        String filePath = System.getProperty("FILE_PATH");
        List<String> keys = FileUtil.readUtf8Lines(filePath);
        int count = 0;
        for (String key : keys) {
            count++;
            try {
                awsS3Client.copyObject(srcBucket, key, destBucket, key);
                log.info("{}. {} ????????????", count, key);
            } catch (Exception e) {
                log.error("error: {} ", key, e);
            }
        }
    }

    public static void deleteEncodeKeys() {
        int count = 0;
        List<String> keys = FileUtil.readUtf8Lines("D:\\code\\java-learning\\out\\artifacts\\java_learning_jar\\encodekeys.log");
        String bucket = "gam_currentdata";
        for (String key : keys) {
            if (StrUtil.isEmpty(key)) {
                continue;
            }
            awsS3Client.deleteObject(bucket, key);
            count++;
        }
        log.info("???????????? {} ???", count);
    }

    /**
     * ??????????????????????????????????????? ????????????
     */
    public static void listObjKey() {
        String bucket = "gam_currentdata";
        String result = "";
        List<String> prefixs = Arrays.asList(
                "sy20160831/01022017/",
                "sy20160831/01022018/",
                "sy20160831/01152018/",
                "sy20160831/01162018/",
                "sy20160831/01172018/",
                "sy20160831/01192018/",
                "sy20160831/01202017/",
                "sy20160831/01202019/",
                "sy20160831/01212017/",
                "sy20160831/04242017/",
                "sy20160831/05252018/"
        );

        for (String prefix : prefixs) {
            String keys = listObjects(bucket, prefix);
            result = result + "\n" + keys + "\n************************************";
        }

        FileUtil.writeString(result, new File("D:\\data\\lesskeys.log"), StandardCharsets.UTF_8);

    }

    /**
     * ?????? ??? .zip ????????????????????????
     *
     * @param bucket
     * @param prefix
     * @return
     */
    public static String listObjects(String bucket, String prefix) {

        List<String> objKeys = new ArrayList<>();
        ObjectListing objectListing = awsS3Client.listObjects(bucket, prefix);
        for (S3ObjectSummary objectSummary : objectListing.getObjectSummaries()) {
            String key = objectSummary.getKey();
            if (key.endsWith(".zip")) {
                objKeys.add(key);
            }
        }
        String join = StrUtil.join("\n", objKeys);

        return prefix + ":\n" + join;
    }

    /**
     * ??????excel???????????????????????????
     */
    public static void countExcelObject() {
        String bucket = "gam-his-222-f";
        String readExcelPath = "D:\\data\\src.xlsx";
        String writeExcelPath = "D:\\data\\dest.xlsx";
        final List<ExcelData> dataList = new ArrayList<>();
        List<String> encodeKeyList = new ArrayList<>();
        EasyExcel.read(readExcelPath, ExcelData.class, new AnalysisEventListener<ExcelData>() {
            @Override
            public void invoke(ExcelData excelData, AnalysisContext analysisContext) {
                dataList.add(excelData);
            }

            @Override
            public void doAfterAllAnalysed(AnalysisContext analysisContext) {

            }
        }).doReadAll();

        log.info("excel ??? {} ?????????", dataList.size());

        for (ExcelData excelData : dataList) {
            if (StrUtil.isEmpty(excelData.getPath())) {
                break;
            }
            log.info(JSON.toJSONString(excelData));
            Map<String, Object> map = countObject(bucket, excelData.getPath());
            log.info("????????? {}", map);
            excelData.setXskyCount((Long) map.get(XSKY_COUNT));
            excelData.setEncodeCodeCount((Long) map.get(ENCODE_COUNT));
            excelData.setDiffCount(excelData.getXskyCount() - excelData.getCount());
            encodeKeyList.add((String) map.get(ENCODE_KEYS));
        }

        String encodeKey = StrUtil.join("\n******************************\n", encodeKeyList);

        FileUtil.writeString(encodeKey, new File("D:\\data\\encodekeys.log"), StandardCharsets.UTF_8);

        EasyExcel.write(writeExcelPath, ExcelData.class)
                .sheet()
                .doWrite(dataList);

    }

    public static Map<String, Object> countObject(String bucket, String prefix) {
        long count = 0;
        long encodeCount = 0;
        List<String> encodeKeyList = new ArrayList<>();
        String continuationToken = "";
        while (true) {
            ListObjectsV2Request listObjectsV2Request = new ListObjectsV2Request();
            listObjectsV2Request.setBucketName(bucket);
            listObjectsV2Request.setPrefix(prefix);
            listObjectsV2Request.setMaxKeys(10000);
            listObjectsV2Request.setContinuationToken(continuationToken);
            ListObjectsV2Result listObjectsV2Result = awsS3Client.listObjectsV2(listObjectsV2Request);

            for (S3ObjectSummary objectSummary : listObjectsV2Result.getObjectSummaries()) {
                if (objectSummary.getKey().endsWith(".zip")) {
                    count += 1;
                }
                if (objectSummary.getKey().contains("%")) {
                    encodeCount += 1;
                    encodeKeyList.add(objectSummary.getKey());
                }
            }

            if (StringUtils.isNullOrEmpty(listObjectsV2Result.getNextContinuationToken())) {
                break;
            }
            continuationToken = listObjectsV2Result.getNextContinuationToken();
        }

        String join = StrUtil.join("\n", encodeKeyList);

        HashMap<String, Object> result = new HashMap<>();
        result.put(XSKY_COUNT, count);
        result.put(ENCODE_COUNT, encodeCount);
        result.put(ENCODE_KEYS, join);
        return result;
    }

    /**
     * ????????????????????????
     */
    public static void listUploads() {
        String bucket = "rlzysclipy1623229510";

        long totalSize = 0;

        ListMultipartUploadsRequest listMultipartUploadsRequest = new ListMultipartUploadsRequest(bucket);
        listMultipartUploadsRequest.setMaxUploads(30000);
        MultipartUploadListing multipartUploadListing = awsS3Client.listMultipartUploads(listMultipartUploadsRequest);
        log.info("upload ????????? " + multipartUploadListing.getMultipartUploads().size());
        for (MultipartUpload multipartUpload : multipartUploadListing.getMultipartUploads()) {
            ListPartsRequest listPartsRequest = new ListPartsRequest(bucket, multipartUpload.getKey(), multipartUpload.getUploadId());

            PartListing partListing = awsS3Client.listParts(listPartsRequest);
            for (PartSummary part : partListing.getParts()) {
                log.info("part size: " + part.getSize());
                totalSize += part.getSize();
            }
        }
        log.info("????????????{}", totalSize);
    }

    /**
     * ????????????meta??????
     */
    public static void headObject() {
        String bucketName = "test";
        String key = "nohup.out";

        try {
            ObjectMetadata objectMetadata = awsS3Client.getObjectMetadata(bucketName, key);
            log.info(objectMetadata.getRawMetadata().toString());
        } catch (AmazonS3Exception e) {
            log.info(e.getMessage());
        }
    }

    /**
     * ??????????????????????????????
     */
    public static void deleteObject() {
        String bucketName = "gam_currentdata";
        String prefix = "sy20160831/";

        String[] secPrefixs = {
                "01012017",
                "01012018",
                "01012019",
                "01022017",
                "01022018",
        };

        String continuationToken = "";

        for (int i = 0; i < secPrefixs.length; i++) {
            String secPrefix = secPrefixs[0];

            String finalPrefix = prefix + secPrefix + "/";

            while (true) {
                ListObjectsV2Request listObjectsV2Request = new ListObjectsV2Request();
                listObjectsV2Request.setBucketName(bucketName);
                listObjectsV2Request.setPrefix(finalPrefix);
                listObjectsV2Request.setDelimiter("/");
                listObjectsV2Request.setMaxKeys(10000);
                listObjectsV2Request.setContinuationToken(continuationToken);

                ListObjectsV2Result listObjectsV2Result = awsS3Client.listObjectsV2(listObjectsV2Request);

                List<String> commonPrefixes = listObjectsV2Result.getCommonPrefixes();

                for (String commonPrefix : commonPrefixes) {
                    ListObjectsV2Result listObjects = awsS3Client.listObjectsV2(bucketName, commonPrefix);
                    for (S3ObjectSummary objectSummary : listObjects.getObjectSummaries()) {
                        awsS3Client.deleteObject(bucketName, objectSummary.getKey());
                    }
                }

                if (StringUtils.isNullOrEmpty(listObjectsV2Result.getContinuationToken())) {
                    break;
                }
                continuationToken = listObjectsV2Result.getContinuationToken();
            }
        }
    }
}
