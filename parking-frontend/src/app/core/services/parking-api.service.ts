import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  CreateSessionRequest,
  CreateSessionResponse,
  HistoryResponse,
  OrderResponse,
  ParkingSession,
  PaymentRow,
  Quote,
  QuoteRequest,
  SessionStatus,
  Vehicle,
  VehicleRequest,
  Zone,
} from '../models';

export interface HistoryFilter {
  month: string;
  vehicleId: string | null;
  status: SessionStatus | null;
  page: number;
  size: number;
}

/** Pozivi parking-service API-ja (zone, vozila, sesije, istorija). */
@Injectable({ providedIn: 'root' })
export class ParkingApi {
  private readonly http = inject(HttpClient);
  private readonly api = environment.apiUrl;

  // ---------- zone ----------
  zones() {
    return firstValueFrom(this.http.get<Zone[]>(`${this.api}/zones`));
  }

  // ---------- vozila ----------
  vehicles() {
    return firstValueFrom(this.http.get<Vehicle[]>(`${this.api}/vehicles`));
  }

  addVehicle(request: VehicleRequest) {
    return firstValueFrom(this.http.post<Vehicle>(`${this.api}/vehicles`, request));
  }

  updateVehicle(id: string, request: VehicleRequest) {
    return firstValueFrom(this.http.put<Vehicle>(`${this.api}/vehicles/${id}`, request));
  }

  deleteVehicle(id: string) {
    return firstValueFrom(this.http.delete<void>(`${this.api}/vehicles/${id}`));
  }

  // ---------- sesije ----------
  quote(request: QuoteRequest) {
    return firstValueFrom(this.http.post<Quote>(`${this.api}/sessions/quote`, request));
  }

  createSession(request: CreateSessionRequest) {
    return firstValueFrom(this.http.post<CreateSessionResponse>(`${this.api}/sessions`, request));
  }

  currentSessions() {
    return firstValueFrom(this.http.get<ParkingSession[]>(`${this.api}/sessions/current`));
  }

  session(id: string) {
    return firstValueFrom(this.http.get<ParkingSession>(`${this.api}/sessions/${id}`));
  }

  order(merchantOrderId: string) {
    return firstValueFrom(
      this.http.get<OrderResponse>(`${this.api}/sessions/orders/${encodeURIComponent(merchantOrderId)}`),
    );
  }

  // ---------- istorija ----------
  history(filter: HistoryFilter) {
    let params = new HttpParams()
      .set('month', filter.month)
      .set('page', filter.page)
      .set('size', filter.size);
    if (filter.vehicleId) params = params.set('vehicleId', filter.vehicleId);
    if (filter.status) params = params.set('status', filter.status);
    return firstValueFrom(this.http.get<HistoryResponse>(`${this.api}/history`, { params }));
  }

  recentPayments(limit = 5) {
    return firstValueFrom(
      this.http.get<PaymentRow[]>(`${this.api}/history/payments`, { params: { limit } }),
    );
  }
}
