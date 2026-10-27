package com.wjh.wakewise.network;

import com.wjh.wakewise.network.Dtos.AlarmDto;
import com.wjh.wakewise.network.Dtos.ApiEnvelope;
import com.wjh.wakewise.network.Dtos.AuthData;
import com.wjh.wakewise.network.Dtos.ConfirmRequest;
import com.wjh.wakewise.network.Dtos.CreateAlarmRequest;
import com.wjh.wakewise.network.Dtos.DeviceEventRequest;
import com.wjh.wakewise.network.Dtos.LoginRequest;
import com.wjh.wakewise.network.Dtos.MorningContextDto;
import com.wjh.wakewise.network.Dtos.PendingPlanDto;
import com.wjh.wakewise.network.Dtos.PlanViewDto;
import com.wjh.wakewise.network.Dtos.RegisterRequest;
import com.wjh.wakewise.network.Dtos.RevertRequest;
import com.wjh.wakewise.network.Dtos.ToggleAlarmRequest;
import com.wjh.wakewise.network.Dtos.UpdateProfileRequest;
import com.wjh.wakewise.network.Dtos.UserProfile;
import com.wjh.wakewise.network.Dtos.WeeklyStatsDto;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface ApiService {

    @POST("auth/register")
    Call<ApiEnvelope<AuthData>> register(@Body RegisterRequest request);

    @POST("auth/login")
    Call<ApiEnvelope<AuthData>> login(@Body LoginRequest request);

    @PATCH("auth/me")
    Call<ApiEnvelope<UserProfile>> updateProfile(@Body UpdateProfileRequest request);

    @GET("auth/me")
    Call<ApiEnvelope<UserProfile>> me();

    @GET("alarms")
    Call<ApiEnvelope<List<AlarmDto>>> listAlarms();

    @POST("alarms")
    Call<ApiEnvelope<AlarmDto>> createAlarm(@Body CreateAlarmRequest request);

    @PATCH("alarms/{id}")
    Call<ApiEnvelope<AlarmDto>> updateAlarm(@Path("id") String alarmId, @Body ToggleAlarmRequest request);

    @GET("morning/context")
    Call<ApiEnvelope<MorningContextDto>> morningContext();

    @GET("device-plans/pending")
    Call<ApiEnvelope<List<PendingPlanDto>>> pendingPlans();

    @POST("device-plans/{planId}/ack")
    Call<ApiEnvelope<PlanViewDto>> ackPlan(@Path("planId") String planId);

    @POST("device-events")
    Call<ApiEnvelope<Void>> reportEvent(@Body DeviceEventRequest request);

    @POST("decisions/{planId}/confirm")
    Call<ApiEnvelope<PlanViewDto>> confirmPlan(@Path("planId") String planId, @Body ConfirmRequest request);

    @POST("decisions/{planId}/revert")
    Call<ApiEnvelope<PlanViewDto>> revertPlan(@Path("planId") String planId, @Body RevertRequest request);

    @GET("statistics/weekly")
    Call<ApiEnvelope<WeeklyStatsDto>> weeklyStats();
}
