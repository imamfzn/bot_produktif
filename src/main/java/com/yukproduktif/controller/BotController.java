
package com.yukproduktif.controller;
import com.yukproduktif.model.*;
import com.yukproduktif.model.body.*;
import com.yukproduktif.model.view.*;
import com.yukproduktif.service.*;
import com.yukproduktif.repository.*;
import com.google.gson.Gson;
import com.linecorp.bot.client.LineSignatureValidator;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 
 * @author Muhammad Imam Fauzan, Rahman Faruq Rajabyansyahr
 * Class controller untuk handle semua perintah yang dikirim dari line follower ke bot.
 * Bot akan selalu "listen" semua aktivitas yang ada di dalam group / multichat / single chat.
 */

@RestController
@RequestMapping(value="/linebot")
public class BotController
{
    @Autowired
    @Qualifier("com.linecorp.channel_secret")
    String lChannelSecret;
    
    @Autowired
    @Qualifier("com.linecorp.channel_access_token")
    String lChannelAccessToken;
    
    @Autowired
    ReminderWajibRepository reminderWajibRepo;
    
    @Autowired
    ReminderSunnahRepository reminderSunnahRepo;
    
    LineBotService botService = new LineBotService();
    AdzanService adzanService = new AdzanService();
    MosqueService mosqueService = new MosqueService();
    
    MainBotView mainView = new MainBotView();
    MenuBotView menuView = new MenuBotView();
    PrayerTimesView adzanView = new PrayerTimesView();
    
    ReminderWajibView reminderWajibView = new ReminderWajibView();
    ReminderSunnahView reminderSunnahView = new ReminderSunnahView();

    @RequestMapping(value="/callback", method=RequestMethod.POST)
    public ResponseEntity<String> callback(
        @RequestHeader("X-Line-Signature") String aXLineSignature,
        @RequestBody String aPayload)
    {
        final String text=String.format("The Signature is: %s",
            (aXLineSignature!=null && aXLineSignature.length() > 0) ? aXLineSignature : "N/A");
        System.out.println(text);
        final boolean valid=new LineSignatureValidator(lChannelSecret.getBytes()).validateSignature(aPayload.getBytes(), aXLineSignature);
        System.out.println("The signature is: " + (valid ? "valid" : "tidak valid"));
        if(aPayload!=null && aPayload.length() > 0)
        {
            System.out.println("Payload: " + aPayload);
        }
        
        //Mengubah data request body menjadi bentuk object
        Gson gson = new Gson();
        Payload payload = gson.fromJson(aPayload, Payload.class);
        
        //Set channel access token to bot service
        //Issue : harusnya kalau bisa gak perlu di set setiap waktu
        //To-do : buat set channel access token on the fly , autowired ?
        botService.setChannelAccessToken(lChannelAccessToken);

        String msgText = " ";
        String idTarget = " ";
        String eventType = payload.events[0].type;

        if (eventType.equals("join")){
            if (payload.events[0].source.type.equals("group")){
            	botService.replyToUser(payload.events[0].replyToken, "Hello Group");
            }
            
            else if (payload.events[0].source.type.equals("room")){
            	botService.replyToUser(payload.events[0].replyToken, "Hello Room");
            }
        } 
        
        //"The main of bot controller"
        //Will handle all of user text request
        else if (eventType.equals("message")){
        	idTarget = getIdTarget(payload);
            if (payload.events[0].message.type.equals("text")){
            	msgText = payload.events[0].message.text.toLowerCase();
            	
            	//handle all request text message from line follower.
            	botAction(msgText, idTarget); 	                
            }
            
            else if (payload.events[0].message.type.equals("location")){
            	//will handle send 5 nearest mosque from user location
            	Double lat = Double.parseDouble(payload.events[0].message.latitude);
            	Double lon = Double.parseDouble(payload.events[0].message.longitude);
            	Location loc = new Location(lat, lon);
            	sendNearestMosque(loc, idTarget);
            }
        }
         
        return new ResponseEntity<String>(HttpStatus.OK);
    }
    
    //Mendapatkan id target user dari response payload yang dikirim oleh LINE.
    private String getIdTarget(Payload payload){
    	String idTarget = "";
        if (payload.events[0].source.type.equals("group")){
            idTarget = payload.events[0].source.groupId;
        } 
        
        else if (payload.events[0].source.type.equals("room")){
            idTarget = payload.events[0].source.roomId;
        } 
        
        else if (payload.events[0].source.type.equals("user")){
            idTarget = payload.events[0].source.userId;
        }
        
        return idTarget;

    }
    
    /**
     * @author Muhammad Imam Fauzan
     *  method untuk menanangani pengaktifan dan nonaktif reminder
     */
    private void reminderHandler(String userId, String reminderRequest){
    	String[] request = reminderRequest.split(" ");
    	String listRequest =  "shubuh dzuhur ashar magrib isya dhuha tahajud";
    	
    	//check request structure.
    	if (request[0].equals("reminder")){
    		if (listRequest.contains(request[1])){
    			//Mengaktifkan / Me-nonaktifkan reminder
    			if (request[2].equals("on") || request[2].equals("off")){
    				boolean newStatus = request[2].equals("on");
    				String prayerName = request[1];
    				changeReminderStatus(userId, prayerName, newStatus);
    			}
    		}
    	}
    }
    
    /**
     * @author Muhammad Imam Fauzan
     * Mengganti status reminder yang sudah ada atupun untuk user yang belum terdaftar pada data reminder.
     * @param userId 	: user id line follower, yang akan dituju
     * @param prayerName : nama waktu adzan / ibadah sunnah
     * @param newStatus	: status baru yang akan di update pada data reminder
     */
    private void changeReminderStatus(String userId, String prayerName, boolean newStatus){
    	final String LIST_WAJIB =  "shubuh dzuhur ashar magrib isya";
    	IReminderRepository repo = null;
    	boolean isWajib = LIST_WAJIB.contains(prayerName);

    	if (isWajib){
    		repo = reminderWajibRepo;
    	}
    	else {
    		repo = reminderSunnahRepo;
    	}
    	
    	IReminder userReminder = repo.findOne(userId);
    
		String reminderRespon = "";
		String reminderType = isWajib ? "adzan " : "shalat ";
		
		//apabila user id sudah terdaftar / sudah pernah mengaktifkan reminder
		if (userReminder != null){
			if (userReminder.isActive(prayerName) != newStatus){
				userReminder.setReminder(prayerName, newStatus);
				try {
					repo.save(userReminder);
					reminderRespon = newStatus == true ? 
							"Reminder untuk " + reminderType + prayerName + " berhasil diaktifkan." :
							"Reminder untuk " + reminderType + prayerName + " berihasil dinon-aktifkan."; 
				}
				catch (Exception ex){
					reminderRespon = newStatus == true ?
							"Gagal mengaktifkan reminder, silahkan coba lagi." :
							"Gagal menonaktifkan reminder, silahkan coba lagi.";
				}
			}
			else {
				reminderRespon = newStatus == true ? 
						"Reminder untuk " + reminderType + prayerName + " sudah aktif." :
						"Reminder untuk " + reminderType + prayerName + " sudah tidak aktif.";
			}
		}
		
		//user id belum terdaftar
		else {
			//mencegah keyword : reminder <prayer_name> off
			//dicegah ketika user belum terdaftar pada reminder, tapi menggunkan keyword off.
			if (newStatus == false){
				reminderRespon = "anda belum mengaktifkan reminder, silahkan aktifkan terlebih dahulu.";
			}
			
			//mengaktifkan reminder untuk pertama kali
			//mendaftarkan user ke data reminder
			//keyword : reminder <prayer_name> on
			else {
				try {
					IReminder newUserReminder;
					if (isWajib){
						newUserReminder = new ReminderWajib(userId);
					}
					else {
						newUserReminder = new ReminderSunnah(userId);
					}
					
					newUserReminder.setReminder(prayerName, true);
					repo.save(newUserReminder);
					
					reminderRespon = "Reminder untuk " + reminderType + prayerName + " berhasil diaktifkan.";
				} 
				catch (Exception ex){
					reminderRespon = "Gagal mengaktifkan reminder, silahkan coba lagi.";
				}	
			}
		}
		
		//send respon reminder to user..
		botService.pushMessage(userId, reminderRespon);
    }
    
    /**
     * Reminder handler dari service reminder, perlu kesepakatan struktur yang dikirim untuk dapat selesai diimplementasi
     * @author Muhammad Imam Fauzan, Rahman Faruq Rajabyansyahr
     * Menerima data yang dikirim dari service reminder, untuk broadcast reminder ke semua line follower bot
     * @param data : request body dari reminder service, yang isinya bakal di handle, untuk broadcast ke line follower
     * @return HttpStatus
     */
    @RequestMapping(value="/reminder", method=RequestMethod.POST)
    public ResponseEntity<String> reminder(@RequestBody String data){
    	/* DEBUG */
        Gson gson = new Gson();
        ReminderRequest reminderRequest = gson.fromJson(data, ReminderRequest.class);
    	//String ID_TARGET = "Ccb45584fc566bd5270591a3d010ae4b0";
    	//Set<String> targets = new HashSet<String>();
    	//targets.add("Ccb45584fc566bd5270591a3d010ae4b0");
    	//targets.add("Ue43858bc93d6a8e1b172d57e1b34c853");
        //String MESSAGE = "Saatnya adzan " + reminderRequest.name+ " untuk daerah Bandung dan sekitarnya.";
        String message = "";
        List<String> targets = null;
        if (reminderRequest.type.equals("fardh")){
        	boolean isPreReminder = !reminderRequest.status;
        	if (isPreReminder){
        		message = "10 Menit lagi memasuki waktu adzan " + reminderRequest.name + " untuk daerah Bandung dan Sekitarnya, ayo segera bersiap-siap";		
        	} else {
        		message = "Saatnya adzan " + reminderRequest.name + " untuk daerah Bandung dan Sekitarnya, ayo mari kita shalat";
        	}
        	        	
        	
        	//reminderWajibRepo.
        }
        else {
        	message = "Ayo mari kita laksanakan shalat " + reminderRequest.name;
        }
        
        targets = getUserReminder(reminderRequest.type, reminderRequest.name);
        botService.setChannelAccessToken(lChannelAccessToken);
        for (String userId : targets){
        	botService.pushMessage(userId, message);
        }    	
    	
    	/**
    	 * to-do :
    	 * Handle setiap jenis reminder yang masuk dari service reminder
    	 * Query ke database, line follower yang mengaktifkan reminder
    	 * Kirim reminder ke line follower ..
    	 */
    	return new ResponseEntity<String>(HttpStatus.OK);
    }
    
    private List<String> getUserReminder(String type, String prayerName){
    	if (type.equals("fardh")){
    		switch(prayerName) {
    			case "shubuh" :  return reminderWajibRepo.findByShubuh();
    			case "dzuhur" :  return reminderWajibRepo.findByDzuhur();
    			case "ashar" :  return reminderWajibRepo.findByAshar();
    			case "magrib" :  return reminderWajibRepo.findByMagrib();
    			case "isya" :  return reminderWajibRepo.findByIsya();
    		}
    	}
    	else {
    		switch(prayerName) {
    			case "dhuha" :  return reminderSunnahRepo.findByDhuha();
    			case "tahajud" :  return reminderSunnahRepo.findByTahajud();
    		}
    	}
    	return null;
    }
    
    @RequestMapping(value="/test", method=RequestMethod.GET)
    public ResponseEntity<List<String>> test(){
    	List<String> users = reminderWajibRepo.findByShubuh();
    	return new ResponseEntity<List<String>>(users,HttpStatus.OK);
 
    }

    /**
     * @author Muhammad Imam Fauzan
     * Menghandle request message yang dikirim dari user line ke bot
     * @param message : request message yang dikirim dari user line
     */
    private void botAction(String message, String userId){
    	if (message.equals("bot menu")){
    		botService.sendTemplateMessage(userId, menuView.getViewMessage());
    	}
    	else if (message.equals("jadwal shalat")){
    		sendPrayerTimes(userId);
    	}
    	else if (message.equals("reminder wajib")){
    		sendReminderWajibView(userId);
    	}
    	else if (message.equals("reminder sunnah")){
    		sendReminderSunnahView(userId);
    	}
    	else if (message.contains("reminder")){
    		//mengaktifkan / non-aktifkan reminder (wajib, sunnah)
    		reminderHandler(userId, message);
    	}
    	else if (message.equals("masjid terdekat")){
    		String helper = "Silahkan kirimkan lokasi anda.";
    		botService.pushMessage(userId, helper);
    	}
    	else if (message.equals("wellcome")){
    		botService.sendTemplateMessage(userId, mainView.getViewMessage());
    	}
    	
    }
    
    //To-Do
    //Kirim informasi adzan yang di request dari user
    //Panggil service adzan
    //Kirim data adzan yang udah diambil dari service adzan pake LineBotService1
    //Masih Prototype, nunggu struktur return dari service adzan fix.
    private void sendPrayerTimes(String userId){
    	PrayerTimesView prayerView = new PrayerTimesView(adzanService.getPrayerTimes());
    	botService.sendTemplateMessage(userId, prayerView.getViewMessage());
		botService.pushMessage(userId, prayerView.getTextMessage());
		
    }
    
    private void sendReminderWajibView(String userId){
    	try {
			ReminderWajib reminder = reminderWajibRepo.findByUserId(userId);
			if (reminder != null){
				botService.sendTemplateMessage(userId, new ReminderWajibView(reminder).getViewMessage());
			}
			else {
				botService.sendTemplateMessage(userId, new ReminderWajibView().getViewMessage());
			}
		}
		
		catch (Exception ex){
			ex.printStackTrace();
		}          
    }
    
    private void sendReminderSunnahView(String userId){
    	try {
    		ReminderSunnah reminder = reminderSunnahRepo.findByUserId(userId);
    		if (reminder != null){
    			botService.sendTemplateMessage(userId, new ReminderSunnahView(reminder).getViewMessage());
    		}
    		else {
    			botService.sendTemplateMessage(userId, new ReminderSunnahView().getViewMessage());
    		}
    	}
    	
    	catch (Exception ex){
    		ex.printStackTrace();
    	}
		    	
    }
    
    private void sendNearestMosque(Location loc,  String userId){
    	botService.sendTemplateMessage(userId, new MosqueView(new MosqueService().FindMosque(loc)).getViewMessage());
    }
    
}
