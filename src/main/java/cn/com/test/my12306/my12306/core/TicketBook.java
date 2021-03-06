/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
package cn.com.test.my12306.my12306.core;

import cn.com.test.my12306.my12306.core.util.JsonBinder;
import cn.com.test.my12306.my12306.core.util.mail.MailUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;

import javax.swing.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * 下单参考 ：https://www.jianshu.com/p/6b1f94e32713
 * 和    http://www.cnblogs.com/small-bud/p/7967650.html
 */
public class TicketBook implements  Runnable{

    public JsonBinder jsonBinder = JsonBinder.buildNonNullBinder(false);

    private BlockingQueue<Map<String,String>> queue ;
    private CloseableHttpClient httpclient;
    private Header[] headers;
    private ClientTicket ct;
    private String bookRancode="";
    private static Logger logger = LogManager.getLogger(TicketBook.class);

    CommonUtil commonUtil = new CommonUtil();

    public TicketBook(ClientTicket ct,BlockingQueue<Map<String, String>> queue, CloseableHttpClient httpclient, Header[] headers) {
        this.ct = ct;
        this.queue = queue;
        this.httpclient = httpclient;
        this.headers = headers;
        if(this.headers.length!=7){
            this.headers = new BasicHeader[7];
            this.headers[0] = new BasicHeader("User-Agent","Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/63.0.3239.132 Safari/537.36");
            this.headers[1] = new BasicHeader("Host","kyfw.12306.cn");
            this.headers[2] = new BasicHeader("Referer","https://kyfw.12306.cn/otn/login/init");
            this.headers[3] = new BasicHeader("Accept","*/*");
            this.headers[4] = new BasicHeader("Accept-Encoding","gzip, deflate");
            this.headers[5] = new BasicHeader("Accept-Language","zh-Hans-CN,zh-Hans;q=0.8,en-US;q=0.5,en;q=0.3");
            this.headers[6] = new BasicHeader("Content-Type","application/x-www-form-urlencoded");
        }else{
            this.headers[2] = new BasicHeader("Referer","https://kyfw.12306.cn/otn/login/init");
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void run(){
            String orderId ="";
            try{
                kaishi:
                while(orderId.equals("") && queue.size()>0){
                Map<String,String> map =null;
                map= queue.take();//获取的整个车次信息
                //校验是否登陆 略
                if(subOrder(map.get("secret"))) { //跳转到提交订单页

                    String token = initDc();//globalRepeatSubmitToken,key_check_isChange
                    String globalRepeatSubmitToken = token.split(",")[0];
                    String key_check_isChange = token.split(",")[1];
                    //获取乘客信息 略
                    getPassenger(globalRepeatSubmitToken);

                    //确认提交订单信息
                    //选择乘客提交 toBuySeat
                    String rsCode = "B";
                    redo:

                    while (rsCode.equals("B")) {
                        String rs = tijiao(globalRepeatSubmitToken, map.get("toBuySeat"));//Y 需要验证码 N不需要  X预订失败
                        rsCode = rs;
                        if (rs.equals("Y")) {
                            //获取验证码
                            boolean checkedCode=false;
                            while(!checkedCode) {

                                //获取验证码
                                String valicode = getCode("", headers);

                                System.out.println("验证码：" + valicode);


                                //校验验证码
                                HttpUriRequest checkCode = RequestBuilder.post()
                                        .setUri(new URI("https://kyfw.12306.cn/otn/passcodeNew/checkRandCodeAnsyn"))
                                        .addHeader(headers[0]).addHeader(headers[1]).addHeader(headers[2]).addHeader(headers[3]).addHeader(headers[4]).addHeader(headers[5]).addHeader(headers[6])
                                        .addParameter("randCode", valicode)
                                        .addParameter("REPEAT_SUBMIT_TOKEN", globalRepeatSubmitToken)
                                        .addParameter("rand", "randp")
                                        .build();
                                CloseableHttpResponse response = httpclient.execute(checkCode);

                                Map<String, Object> rsmap = null;
                                try {
                                    HttpEntity entity = response.getEntity();
                                    String responseBody=EntityUtils.toString(entity);
                                    rsmap = this.jsonBinder.fromJson(responseBody, Map.class);
//                System.out.println("校验：" + response.getStatusLine().getStatusCode() + " " + entity.getContent() + " abc " + EntityUtils.toString(entity));
                                    if ((rsmap.get("status")+"").equalsIgnoreCase("true")) {
                                        Map<String, Object> dataMap = (Map<String, Object>)rsmap.get("data");
                                        String msg = rsmap.get("msg")+"";
                                        if(msg.equalsIgnoreCase("TRUE")){
                                        System.out.println("验证码校验通过");
                                        checkedCode=true;
                                        }
                                    } else {
                                        System.out.println("验证码校验没有通过");
                                    }
                                } catch (Exception e) {
                                    System.out.println("验证码校验没有通过");
                                    e.printStackTrace();
                                } finally {
                                    response.close();
                                }
                            }

                        }
                        if (rs.equals("X")) {
                            //预订失败 直接返回
                            System.out.println("预定失败 返回X");
                            rsCode = "B";
                            continue redo;
                        }
                    }
                    //getQueue 略
                    getQueueCount(globalRepeatSubmitToken, map);
                    //确认订单信息
                    confirmSingle(globalRepeatSubmitToken, key_check_isChange, map.get("toBuySeat"), map);

                    //进入排队等待
                    orderId = waitOrder(globalRepeatSubmitToken);
                    orderId=orderId.equals("null")?"":orderId;
                    System.out.println("获取的订单Id：" + orderId);
                    if (!orderId.equals("")) {
                        //订票成功 退出程序
                        System.out.println("购票成功，订单Id：" + orderId+",赶紧支付去吧");
//                        new TipTest("","","订票成功，订单号："+orderId);
                        ct.sendSuccessMail("购票成功，订单ID："+orderId);
                        Thread.sleep(3000);
                        System.exit(0);
                    } else {
                        //重新开始查询
                        continue kaishi;
//                        ct.reshua(headers);

                    }
                }

                }
                if(orderId.equals("")){//queue 取完了 还没有订单 重新刷
                    ct.reshua(headers);
                }

                Thread.sleep(200L);
            }catch (Exception e){
                System.out.println("预定时出錯");
               e.printStackTrace();
            }


    }

    /**
     * 获取验证码弹窗
     * @param url
     * @return
     * @throws IOException
     */
    public String getCode(String url,Header[] headers) throws IOException {
        //JFrame frame = new JFrame("验证码");
        this.bookRancode="";
        System.out.println("获取验证码的地址："+url);
//        new TipTest("","","请输入验证码");
        JLabel label = new JLabel(new ImageIcon(getCodeByte(url,headers)),
                JLabel.CENTER);

        label.setBounds(0, 0, 295, 220);
        label.setVerticalAlignment(SwingConstants.TOP);
        label.addMouseListener(new RecordListener());

        JOptionPane.showConfirmDialog(null, label,
                "请输入验证码", JOptionPane.DEFAULT_OPTION);
		/*InputStreamReader isr = new InputStreamReader(System.in);
		BufferedReader br = new BufferedReader(isr);
		String rd = br.readLine();*/
        //frame.dispose();
        return this.bookRancode;
    }


    /**
     * 获取指定url的验证码图片字节信息
     * @param url
     * @return
     */

    public byte[] getCodeByte(String url,Header[] headers) {
        HttpGet get = new HttpGet(url);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] bytse = null;
        try {
            HttpGet hget = new HttpGet("https://kyfw.12306.cn/otn/passcodeNew/getPassCodeNew?module=passenger&rand=randp&" + Math.random());
            for(Header h:headers){
                hget.addHeader(h);
            }
//                CloseableHttpResponse response = httpclient.execute(new HttpGet("https://"+hosts+"/passport/captcha/captcha-image?login_site=E&module=login&rand=sjrand&" + Math.random()));
            CloseableHttpResponse response = httpclient.execute(hget);

            HttpEntity entity = response.getEntity();
            bytse=  EntityUtils.toByteArray(entity);
            EntityUtils.consume(entity); //Consume response content
        }catch(Exception e ){
            e.printStackTrace();
        }
        return bytse;

    }

    /**
     *	鼠标点击验证码，记录坐标
     *
     * @author xiaoQ
     * @since 2011-12-21
     * @version 1.0
     */
    class RecordListener extends MouseAdapter {
        @Override
        public void mouseClicked(MouseEvent e) {
            //refreshRandCode();
            int x=e.getX();
            int y=e.getY();
            bookRancode+=bookRancode.equals("")?x+","+(y-30):","+x+","+(y-30);
            System.out.println(x+","+y+"  rancode:"+bookRancode);
        }
    }

    public void resetRancode(){
        this.bookRancode = "";
    }

    /**
     * 获取乘客列表
     * @return
     */
    public List<Map<String,String>> getPassenger(String token){
        CloseableHttpResponse response=null;
        List<Map<String,String>> users =null;
        try {
        HttpUriRequest checkCode = RequestBuilder.post()
                .setUri(new URI("https://kyfw.12306.cn/otn/confirmPassenger/getPassengerDTOs"))
                .addHeader(headers[0]).addHeader(headers[1]).addHeader(headers[2]).addHeader(headers[3]).addHeader(headers[4]).addHeader(headers[5]).addHeader(headers[6])
                .addParameter("REPEAT_SUBMIT_TOKEN", token)
                .addParameter("_json_att", "")
                .build();
        response = httpclient.execute(checkCode);

        Map<String, Object> rsmap = null;

            HttpEntity entity = response.getEntity();
            String responseBody = EntityUtils.toString(entity);
            rsmap = jsonBinder.fromJson(responseBody, Map.class);
            if (rsmap.get("status").toString().equalsIgnoreCase("true")) {
               Map<String,Object> dataMap = (Map<String,Object>)rsmap.get("data");
               /*
                code	10
                passenger_name	张无忌
                sex_code	M
                sex_name	男
                born_date	2017-08-25 00:00:00
                country_code	CN
                passenger_id_type_code	1
                passenger_id_type_name	二代身份证
                passenger_id_no	130XXXX
                passenger_type	1
                passenger_flag	0
                passenger_type_name	成人
                mobile_no
                phone_no
                email
                address
                postalcode
                first_letter	ZJQ
                recordCount	13
                total_times	99
                index_id	0
                */
               users=(List<Map<String,String>>)dataMap.get("normal_passengers");
                System.out.println("获取用户乘客信息完成"+responseBody);

            } else {
                System.out.println("获取用户乘客信息失败"+responseBody);
            }
        }catch (Exception e){
            System.out.println("获取用户乘客信息失败1");
            e.printStackTrace();
        }finally {
            try{
            response.close();
            }catch (Exception e){

            }
        }
        return users;
    }

    /**
     * 自动预订 掉线后不能自动提交 （模拟官方订票助手）
     * @param seat 要预定的席别
     */
    public boolean autoSubmi(String secretStr,String seat){
        boolean flag=true;
        CloseableHttpResponse response=null;
        try {
            List<Map<String,String>> userList =getPassenger("");
            String[] users =commonUtil.getUser().split(",");
            String oldPassengerStr="";//姓名，证件类别，证件号码，用户类型
            String passengerTicketStr="";//座位类型，0，车票类型，姓名，身份正号，电话，N（多个的话，以逗号分隔）
            for(Map<String,String> u:userList){
                for(String u1:users){
                    if(u1.equals(u.get("passenger_name"))){
                        oldPassengerStr+=u.get("passenger_name")+","+u.get("passenger_id_type_code")+","+u.get("passenger_id_no")+","+u.get("passenger_type")+"_";
                        passengerTicketStr+=commonUtil.seatMap.get(seat)+",0,1,"+u.get("passenger_name")+","+u.get("passenger_id_type_code")+","+u.get("passenger_id_no")+","+u.get("mobile_no")+",N_";
                    }
                }
            }
            passengerTicketStr=passengerTicketStr.endsWith("_")?passengerTicketStr.substring(0,passengerTicketStr.length()-1):passengerTicketStr;

            HttpUriRequest autoSubmi = RequestBuilder.post()
                    .setUri(new URI("https://kyfw.12306.cn/otn/confirmPassenger/autoSubmitOrderRequest"))
                    .addHeader(headers[0]).addHeader(headers[1]).addHeader(headers[2]).addHeader(headers[3]).addHeader(headers[4]).addHeader(headers[5]).addHeader(headers[6])
                    .addParameter("bed_level_order_num", "000000000000000000000000000000")
                    .addParameter("cancel_flag", "2")
                    .addParameter("oldPassengerStr", oldPassengerStr)
                    .addParameter("passengerTicketStr", passengerTicketStr)
                    .addParameter("purpose_codes", "ADULT")
                    .addParameter("query_from_station_name", commonUtil.getFrom())
                    .addParameter("query_to_station_name", commonUtil.getTo())
                    .addParameter("secretStr",  secretStr)
                    .addParameter("tour_flag",  "dc")
                    .addParameter("train_date",  commonUtil.getDate())
                    .build();
            response = httpclient.execute(autoSubmi);

            Map<String, Object> rsmap = null;

            HttpEntity entity = response.getEntity();
            rsmap = jsonBinder.fromJson(EntityUtils.toString(entity), Map.class);
            if (rsmap.get("status").toString().equalsIgnoreCase("true")) {
                Map<String,Object> dataMap = (Map<String,Object>)rsmap.get("data");
                String drs=dataMap.get("result")+"";
                String ifShowPassCode=dataMap.get("ifShowPassCode")+"";//是否需要验证码 Y需要 N不需要
                String ifShowPassCodeTime=dataMap.get("ifShowPassCodeTime")+"";//不知道是否要等待这么久2801
                if(ifShowPassCode.equals("Y")){
                    //验证码
                }
                //获取余票信息 不是必须？

                //post https://kyfw.12306.cn/otn/confirmPassenger/confirmSingleForQueueAsys 生成车票 可能会302


                //get https://kyfw.12306.cn/otn/confirmPassenger/queryOrderWaitTime?random=1517580650391&tourFlag=dc&_json_att= 查询订单信息


            } else {
                System.out.println("自动预订失败");
                flag=false;
            }
        }catch (Exception e){
            flag=false;
            System.out.println("自动预订出错");
            e.printStackTrace();
        }finally {
            try{
                response.close();
            }catch (Exception e){

            }
        }
        return flag;
    }

    /**
     * 檢查用户在线状态
     *
     * @return true 在线;false 掉线了
     */
    public  boolean checkUser(){
        CloseableHttpResponse response=null;
        try{


        HttpUriRequest checkUser = RequestBuilder.post()
                .setUri(new URI("https://kyfw.12306.cn/otn/login/checkUser"))
                .addHeader(headers[0]).addHeader(headers[1]).addHeader(headers[2]).addHeader(headers[3]).addHeader(headers[4]).addHeader(headers[5]).addHeader(headers[6])
                .addParameter("_json_att", "")
                .build();
        response = httpclient.execute(checkUser);

        Map<String, Object> rsmap = null;

        HttpEntity entity = response.getEntity();
        rsmap = jsonBinder.fromJson(EntityUtils.toString(entity), Map.class);
            if (rsmap.get("status").toString().equals("true")) {
                Map<String,Object> dataMap = (Map<String,Object>)rsmap.get("data");
               return (boolean) dataMap.get("flag");

            }
        }catch (Exception e){
            System.out.println("檢查用戶狀態失敗");
            e.printStackTrace();
        }finally {
            try{
                response.close();
            }catch (Exception e){

            }
        }
        return false;
    }

    /**
     * 不发送此请求直接跳转会报错
     *
     * @return
     */
    public boolean subOrder(String secretStr){
        CloseableHttpResponse response=null;


        try {
            //secretStr 需要解码
//            secretStr= URLDecoder.decode(secretStr,"utf-8");

            HttpUriRequest checkUser = RequestBuilder.post()
                    .setUri(new URI("https://kyfw.12306.cn/otn/leftTicket/submitOrderRequest"))
                    .addHeader(headers[0]).addHeader(headers[1]).addHeader(headers[2]).addHeader(headers[3]).addHeader(headers[4]).addHeader(headers[5]).addHeader(headers[6])

                    .addParameter("back_train_date", commonUtil.getToday())
                    .addParameter("purpose_codes", "ADULT")
                    .addParameter("query_from_station_name", commonUtil.getFrom())
                    .addParameter("query_to_station_name", commonUtil.getTo())
                    .addParameter("secretStr", secretStr)
                    .addParameter("tour_flag", "dc")
                    .addParameter("train_date", commonUtil.getDate())
                    .addParameter("undefined", "")
                    .build();
            response = httpclient.execute(checkUser);

            Map<String, Object> rsmap = null;

            HttpEntity entity = response.getEntity();
            String responseBody = EntityUtils.toString(entity);
            if (!"".equals(responseBody)){
                rsmap = jsonBinder.fromJson(responseBody, Map.class);
//            System.out.println("预定时候出错了？："+responseBody);
            if (null != rsmap.get("status") && rsmap.get("status").toString().equals("true")) {
                System.out.println("点击预定按钮成功：" + responseBody);
                return true;

            } else if (null != rsmap.get("status") && rsmap.get("status").toString().equals("false")) {
                String errMsg = rsmap.get("messages") + "";
                System.out.println("mmmmm" + errMsg + " fffffffffff " + errMsg.contains("未处理的订单"));
                if (errMsg.contains("未处理的订单")) {
//                    new TipTest("","","您有未处理订单，请查询");
                    System.out.println("您有未完成订单，请处理");
                    System.exit(0);
                } else if (errMsg.contains("当前时间不可以订票")) {
                    System.out.println("系统维护时间不能订票");
                    System.exit(0);
                }
            } else {
                System.out.println("预定时候出错了：" + responseBody);
            }
        }else{
                System.out.println("点击预定按钮失败了，查看是否被禁");
                return false;
            }
        }catch (Exception e){
            System.out.println("点击预定按钮成功");
            e.printStackTrace();
        }finally {
            try{
                response.close();
            }catch (Exception e){

            }
        }
        return false;
    }

    /**
     * 进入下单页面
     *
     * @return token,key_check_isChange
     */
    public String initDc(){
        CloseableHttpResponse response=null;
        String token ="";
        String responseBody = "";
        try{
            HttpUriRequest confirm = RequestBuilder.post()
                    .setUri(new URI("https://kyfw.12306.cn/otn/confirmPassenger/initDc"))
                    .addHeader(headers[0]).addHeader(headers[1]).addHeader(headers[2]).addHeader(headers[3]).addHeader(headers[4]).addHeader(headers[5]).addHeader(headers[6])
                    .addParameter("_json_att", "")
                    .build();
            response = httpclient.execute(confirm);



            if(response.getStatusLine().getStatusCode()==200){
                HttpEntity entity = response.getEntity();
                responseBody =EntityUtils.toString(entity);
                System.out.println("initDc成功");
                Pattern p=Pattern.compile("globalRepeatSubmitToken \\= '(.*?)';");
                Matcher m=p.matcher(responseBody);
                while(m.find()){
                    token=m.group(1);
                }
                Pattern p1=Pattern.compile("'key_check_isChange':'(.*?)',");
                Matcher m1=p1.matcher(responseBody);
                while(m1.find()){
                    token+=","+m1.group(1);
                }
                this.headers[2] = new BasicHeader("Referer","https://kyfw.12306.cn/otn/confirmPassenger/initDc");
            }else{
                System.out.println("initDc失败 status错误");
            }

        }catch (Exception e){
            System.out.println("initDc失败"+responseBody);
            e.printStackTrace();
        }finally {
            try{
                response.close();
            }catch (Exception e){

            }
        }
        return token;

    }
    /**
     *
     * 选择乘客、票种提交
     * @param token token initDc获取
     * @param seat  座位类型
     * @return 是否需要验证码 Y需要 N不需要 X:预订失败
     */
    public synchronized String tijiao(String token,String seat){
        CloseableHttpResponse response=null;
        String rs="X";
        String responseBody="";
        try{

            List<Map<String,String>> userList =getPassenger("");
            String[] users =commonUtil.getUser().split(",");
            String oldPassengerStr="";//姓名，证件类别，证件号码，用户类型
            String passengerTicketStr="";//座位类型，0，车票类型，姓名，身份正号，电话，N（多个的话，以逗号分隔）
            for(Map<String,String> u:userList){
                for(String u1:users){
                    if(u1.equals(u.get("passenger_name"))){
                        oldPassengerStr+=u.get("passenger_name")+","+u.get("passenger_id_type_code")+","+u.get("passenger_id_no")+","+u.get("passenger_type")+"_";
                        passengerTicketStr+=CommonUtil.seatMap.get(seat)+",0,1,"+u.get("passenger_name")+","+u.get("passenger_id_type_code")+","+u.get("passenger_id_no")+","+u.get("mobile_no")+",N_";
                    }
                }
            }
            passengerTicketStr=passengerTicketStr.endsWith("_")?passengerTicketStr.substring(0,passengerTicketStr.length()-1):passengerTicketStr;
            /*
            whatsSelect 1 成人票 0：学生票
            tour_flag dc 单程

             */
            HttpUriRequest checkOrder = RequestBuilder.post()
                    .setUri(new URI("https://kyfw.12306.cn/otn/confirmPassenger/checkOrderInfo"))
                    .addHeader(headers[0]).addHeader(headers[1]).addHeader(headers[2]).addHeader(headers[3]).addHeader(headers[4]).addHeader(headers[5]).addHeader(headers[6])
                    .addParameter("bed_level_order_num", "000000000000000000000000000000")
                    .addParameter("cancel_flag", "2")
                    .addParameter("oldPassengerStr", oldPassengerStr)
                    .addParameter("passengerTicketStr", passengerTicketStr)
                    .addParameter("randCode", "")
                    .addParameter("REPEAT_SUBMIT_TOKEN", token)
                    .addParameter("tour_flag", "dc")
                    .addParameter("whatsSelect", "1")
                    .addParameter("_json_att", "")
                    .build();
            response = httpclient.execute(checkOrder);

            Map<String, Object> rsmap = null;
            HttpEntity entity = response.getEntity();
            responseBody = EntityUtils.toString(entity);
            rsmap = jsonBinder.fromJson(responseBody, Map.class);
            if (rsmap.get("status").toString().equalsIgnoreCase("true")) {
                Map<String,Object> dataMap = (Map<String,Object>)rsmap.get("data");
                String drs=dataMap.get("result")+"";
                String ifShowPassCode=dataMap.get("ifShowPassCode")+"";//是否需要验证码 Y需要 N不需要
                String ifShowPassCodeTime=dataMap.get("ifShowPassCodeTime")+"";//不知道是否要等待这么久2801
                if(ifShowPassCode.equals("Y")){
                    //验证码
                    rs="Y";
                }else{
                    rs="N";
                }
                logger.info("是否需要验证码："+rs+" 需要等待安全期："+ifShowPassCodeTime);
                Thread.sleep(Integer.parseInt(ifShowPassCodeTime));
                //获取余票信息 不是必须？

                //post https://kyfw.12306.cn/otn/confirmPassenger/confirmSingleForQueueAsys 生成车票 可能会302


                //get https://kyfw.12306.cn/otn/confirmPassenger/queryOrderWaitTime?random=1517580650391&tourFlag=dc&_json_att= 查询订单信息


            } else {
                System.out.println("选择乘客提交订单失败"+responseBody);
                System.out.println("选择乘客提交订单失败"+rsmap.get("status")+" "+rsmap.get("messages"));
                rs="X";
            }

        }catch (Exception e){
            System.out.println("选择乘客提交订单失败"+responseBody);
            e.printStackTrace();
            rs="X";
        }finally {
            try{
                response.close();
            }catch (Exception e){

            }
        }
        return rs;

    }

    /**
     * 获取排队和余票信息
     * @param token
     * @param map
     * @return 余票不够时的提示信息，空表示余票够
     */
    public String getQueueCount(String token,Map<String,String> map){
        CloseableHttpResponse response=null;
        Map<String,Object> rsMap= new HashMap<String,Object>();
        String responseBody ="",rs ="";
        try{
            HttpUriRequest confirm = RequestBuilder.post()
                    .setUri(new URI("https://kyfw.12306.cn/otn/confirmPassenger/getQueueCount"))
                    .addHeader(headers[0]).addHeader(headers[1]).addHeader(headers[2]).addHeader(headers[3]).addHeader(headers[4]).addHeader(headers[5]).addHeader(headers[6])
                    .addParameter("fromStationTelecode", map.get("fromStationTelecode"))
                    .addParameter("toStationTelecode", map.get("toStationTelecode"))
                    .addParameter("leftTicket", map.get("leftTicket"))
                    .addParameter("purpose_codes", "00")
                    .addParameter("REPEAT_SUBMIT_TOKEN", token)
                    .addParameter("seatType", CommonUtil.seatMap.get(map.get("toBuySeat")))
                    .addParameter("stationTrainCode", map.get("chehao"))
                    .addParameter("train_date", getGMT(commonUtil.getDate()))//时间格式待定 Sun+Feb+25+2018+00:00:00+GMT+0800
                    .addParameter("train_location", map.get("train_location"))
                    .addParameter("train_no", map.get("train_no"))
                    .addParameter("_json_att", "")
                    .build();
            response = httpclient.execute(confirm);


            HttpEntity entity = response.getEntity();

            if(response.getStatusLine().getStatusCode()==200){

                responseBody =EntityUtils.toString(entity);
                System.out.println("查询排队和余票成功"+responseBody);
                Map<String, Object> rsmap = jsonBinder.fromJson(responseBody, Map.class);
                if (rsmap.get("status").toString().equals("true")) {
                    Map<String, Object> data=(Map<String, Object>)rsmap.get("data");
                    //他们的代码没有加余票是否够买 我也先不加了
                    String yupiao = data.get("")+"";
                }

            }else{
                System.out.println("查询排队和余票失败");
            }

        }catch (Exception e){
            System.out.println("查询排队和余票失败"+responseBody);
            e.printStackTrace();
        }finally {
            try{
                response.close();
            }catch (Exception e){

            }
        }
        return rs;
    }

    public String getGMT(String date){
        String str="";
        TimeZone tz = TimeZone.getTimeZone("ETC/GMT-8");
        TimeZone.setDefault(tz);
//		Calendar cal = Calendar.getInstance();
        Calendar cal = Calendar.getInstance(new SimpleTimeZone(0, "GMT"));
        Date dd;
        SimpleDateFormat shortSdf = new SimpleDateFormat("yyyy-MM-dd");;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT'", Locale.US);
            sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
            dd = shortSdf.parse(date);
            cal.setTime(dd);
            str = sdf.format(cal.getTime());
            return str+"+0800 (中国标准时间)";
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return null;
    }


    //https://github.com/l107868382/tickets/blob/master/src/main/java/com/tickets/tickets/service/impl/TicketsServiceImpl.java

    /**
     * 单程票提交确认
     * 往返票地址 为https://kyfw.12306.cn/otn/confirmPassenger/confirmGoForQueue
     * 请求体相同
     *
     */
    public void confirmSingle(String token,String key_check_isChange,String seat,Map<String,String> map){
        CloseableHttpResponse response=null;
        Map<String,Object> rsMap= new HashMap<String,Object>();
        String  responseBody="";
        try{
            List<Map<String,String>> userList =getPassenger("");
            String[] users =commonUtil.getUser().split(",");
            String oldPassengerStr="";//姓名，证件类别，证件号码，用户类型
            String passengerTicketStr="";//座位类型，0，车票类型，姓名，身份正号，电话，N（多个的话，以逗号分隔）
            for(Map<String,String> u:userList){
                for(String u1:users){
                    if(u1.equals(u.get("passenger_name"))){
                        oldPassengerStr+=u.get("passenger_name")+","+u.get("passenger_id_type_code")+","+u.get("passenger_id_no")+","+u.get("passenger_type")+"_";
                        passengerTicketStr+=CommonUtil.seatMap.get(seat)+",0,1,"+u.get("passenger_name")+","+u.get("passenger_id_type_code")+","+u.get("passenger_id_no")+","+u.get("mobile_no")+",N_";
                    }
                }
            }
            passengerTicketStr=passengerTicketStr.endsWith("_")?passengerTicketStr.substring(0,passengerTicketStr.length()-1):passengerTicketStr;

            HttpUriRequest confirm = RequestBuilder.post()
                    .setUri(new URI("https://kyfw.12306.cn/otn/confirmPassenger/confirmSingleForQueue"))
                    .addHeader(headers[0]).addHeader(headers[1]).addHeader(headers[2]).addHeader(headers[3]).addHeader(headers[4]).addHeader(headers[5]).addHeader(headers[6])
                    .addParameter("choose_seats", "")
                    .addParameter("dwAll", "N")
                    .addParameter("key_check_isChange", key_check_isChange)
                    .addParameter("leftTicketStr", map.get("leftTicket"))
                    .addParameter("oldPassengerStr", oldPassengerStr)
                    .addParameter("passengerTicketStr", passengerTicketStr)
                    .addParameter("purpose_codes", "00")
                    .addParameter("randCode", "")
                    .addParameter("REPEAT_SUBMIT_TOKEN", token)
                    .addParameter("roomType", "00")
                    .addParameter("seatDetailType", "000")
                    .addParameter("train_location", map.get("train_location"))
                    .addParameter("whatsSelect", "1")
                    .addParameter("_json_att", "")
                    .build();
            response = httpclient.execute(confirm);


            HttpEntity entity = response.getEntity();
            responseBody =EntityUtils.toString(entity);
            if(response.getStatusLine().getStatusCode()==200){

                Map<String, Object> rsmap = jsonBinder.fromJson(responseBody, Map.class);
                if (rsmap.get("status").toString().equals("true")) {
                    Map<String, Object> data=(Map<String, Object>)rsmap.get("data");
                    String subStatus = data.get("submitStatus")+"";//true为成功 false为失败 需要查看errMsg
                    if(subStatus.equals("true")){
                        System.out.println("确认提交订单成功"+responseBody);
                    }else{
                        String errMsg =data.get("errMsg")+"";
                        System.out.println("确认提交订单失败"+errMsg+" 返回内容："+responseBody);
                    }
//                    System.out.println("确认提交订单成功"+responseBody);
                }else{
                    System.out.println("确认提交订单失败"+responseBody);
                }

            }else{
                System.out.println("确认提交订单失败"+responseBody);
            }

        }catch (Exception e){
            System.out.println("确认提交订单失败"+responseBody);
            e.printStackTrace();
        }finally {
            try{
                response.close();
            }catch (Exception e){

            }
        }
    }

    public String waitOrder(String token) {
        String orderId = "";
        String waitTime = "0";
        String message ="";
        try {

//            while(orderId.equals("")){
            while (Integer.parseInt(waitTime)>=0) { //是不是-1啊 忘记了
                HttpUriRequest waitOrder = RequestBuilder.get()
                        .setUri("https://kyfw.12306.cn/otn/confirmPassenger/queryOrderWaitTime?random=1519567822886&tourFlag=dc&_json_att=&REPEAT_SUBMIT_TOKEN=" + token)
                        .addHeader(headers[0]).addHeader(headers[1]).addHeader(headers[2]).addHeader(headers[3]).addHeader(headers[4]).addHeader(headers[5]).addHeader(headers[6])
                        .build();

                CloseableHttpResponse response = httpclient.execute(waitOrder);
                HttpEntity entity = response.getEntity();
                String responseBody = EntityUtils.toString(entity);
                Map<String, Object> rsmap = jsonBinder.fromJson(responseBody, Map.class);
                if (rsmap.get("status").toString().equals("true")) {
                    Map<String, Object> data = (Map<String, Object>) rsmap.get("data");
                    waitTime = data.get("waitTime") + "";
                    String waitCount = data.get("waitCount") + "";
                    orderId = data.get("orderId") + "";
                    System.out.println("前面" + waitCount + "人，需等待：" + waitTime + "");
                    message = data.get("msg") + "";
                    Thread.sleep(1000);
                }
            }
            if(orderId.equals("")){
                System.out.println("获取订单号失败："+message);
            }
        } catch (Exception e) {
            System.out.println("查询订单号失败");
            e.printStackTrace();
        }
        return orderId;

    }

}
