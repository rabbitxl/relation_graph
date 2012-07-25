package samecompany;

public class Employee {

	private String userId;
	private String companyName;
	private String businessType;
	private String businessDetail;
	private String positionType;
	private String position;
	private String startDay;
	private String endDay;

	/***
	 *  type = 1 	基本信息
	 *  type = 2	有填写工作时间
	 *  type = 4	有职位信息
	 *  type = 8	有行业信息,但是一般一个公司,应该都是同行业(我不明白这个行业在同一个公司里面会有不同吗?)
	 *  type = 15	包含2,4,8
	 */
	public static final int BASIC = 1;
	public static final int WITHDAY = 2;	// 不关心开始和结束,只关心有没有
	public static final int WITHPOSITION = 4;
	public static final int WITHBUSINESS = 8;
	public static final int FULL = 15;
	public static final int MATCHED = 16;
	private int type;
	
	public String getUserId() {
		return userId;
	}
	public void setUserId(String userId) {
		this.userId = userId;
	}
	public String getCompanyName() {
		return companyName;
	}
	public void setCompanyName(String companyName) {
		this.companyName = companyName;
	}
	public String getBusinessType() {
		return businessType;
	}
	public void setBusinessType(String businessType) {
		this.businessType = businessType;
	}
	public String getBusinessDetail() {
		return businessDetail;
	}
	public void setBusinessDetail(String businessDetail) {
		this.businessDetail = businessDetail;
	}
	public String getPositionType() {
		return positionType;
	}
	public void setPositionType(String positionType) {
		this.positionType = positionType;
	}
	public String getPosition() {
		return position;
	}
	public void setPosition(String position) {
		this.position = position;
	}
	public String getStartDay() {
		return startDay;
	}
	public void setStartDay(String startDay) {
		this.startDay = startDay;
	}
	public String getEndDay() {
		return endDay;
	}
	public void setEndDay(String endDay) {
		this.endDay = endDay;
	}
	public int getType() {
		return type;
	}
	public void setType(int type) {
		this.type = type;
	}
	
}
