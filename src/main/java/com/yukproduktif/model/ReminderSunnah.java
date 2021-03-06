package com.yukproduktif.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "reminder_sunnah1")
public class ReminderSunnah implements IReminder {
	
	@Id
	@Column(name = "reminder_user_id")
	private String userId;
	
	@Column(name = "reminder_dhuha")
	private boolean dhuha;
	
	@Column(name = "reminder_tahajud")
	private boolean tahajud;
	
	public ReminderSunnah(){}
	
	public ReminderSunnah(String userId) {
		this.userId = userId;
	}
	
	public ReminderSunnah(String userId, boolean dhuha, boolean tahajud) {
		this.userId = userId;
		this.dhuha = dhuha;
		this.tahajud = tahajud;
	}
	
	public ReminderSunnah(boolean dhuha, boolean tahajud) {
		this.dhuha = dhuha;
		this.tahajud = tahajud;
	}
	
	public boolean isDhunaActive(){
		return dhuha;
	}
	
	public boolean isTahajudActive(){
		return tahajud;
	}
		
	private void setAllReminder(boolean status){
		dhuha = tahajud = status;
	}
	
	public boolean isActive(String prayerName){
		switch(prayerName){
			case "dhuha" : return dhuha;
			case "tahajud" : return tahajud;
		}
		return false;
	}
	
	public void setReminder(String prayerName, boolean status){
		switch(prayerName){
			case "dhuha" : dhuha = status; break;
			case "tahajud" : tahajud = status; break;
		}
	}

	public void setAllActive(){
		setAllReminder(true);
	}
	
	public void setAllNonActive(){
		setAllReminder(false);
	}
	
	
}
