import { Component, inject, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HistoryResponse, ParkingSession, SessionStatus, StatusLogEntry, Vehicle } from '../../core/models';
import { HistoryFilter, ParkingApi } from '../../core/services/parking-api.service';
import {
  currentMonth,
  date,
  errorMessage,
  lastMonths,
  methodLabel,
  money,
  monthLabel,
  paymentBadge,
  PURPOSE_LABEL,
  SESSION_STATUS,
  time,
  timeSec,
} from '../../shared/format';

const PAGE_SIZE = 10;

/** Opis dogadjaja iz traga promena statusa. */
const EVENT_LABELS: Record<string, string> = {
  ORDER_CREATED: 'Čeka plaćanje',
  PAYMENT_INITIATED: 'Preusmeren na plaćanje',
  PAYMENT_INIT_REJECTED: 'Plaćanje odbijeno',
  SESSION_ACTIVATED: 'Aktivna',
  SESSION_EXPIRED: 'Istekla',
  PAYMENT_FAILED: 'Plaćanje odbijeno',
  PAYMENT_EXPIRED: 'Plaćanje nije završeno na vreme',
  PAYMENT_ERROR: 'Greška u obradi plaćanja',
  PAYMENT_AMOUNT_MISMATCH: 'Iznos se ne poklapa sa nalogom',
  ACTIVATION_CONFLICT: 'Vozilo već ima aktivnu sesiju',
  REFUND_REQUIRED: 'Čeka refundaciju',
  EXTENSION_PAYMENT_CONFIRMED: 'Plaćanje produženja potvrđeno',
};

const ACTOR_LABELS: Record<StatusLogEntry['actor'], string> = {
  USER: 'korisnik',
  PSP: 'potvrda PSP-a',
  SYSTEM: 'sistem',
};

const STATUS_COLORS: Record<SessionStatus, string> = {
  PENDING_PAYMENT: '#8A5300',
  ACTIVE: '#1F6B46',
  EXPIRED: '#38455C',
  CANCELLED: '#A4281F',
};

/** P05 - istorija parkiranja i placanja sa tragom promena statusa. */
@Component({
  selector: 'app-history',
  imports: [FormsModule],
  templateUrl: './history.html',
  styleUrl: './history.scss',
})
export class History implements OnInit {
  private readonly api = inject(ParkingApi);

  protected readonly months = lastMonths(12);
  protected readonly vehicles = signal<Vehicle[]>([]);
  protected readonly data = signal<HistoryResponse | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly expanded = signal<string | null>(null);

  protected readonly filter = signal<HistoryFilter>({
    month: currentMonth(),
    vehicleId: null,
    status: null,
    page: 0,
    size: PAGE_SIZE,
  });

  protected readonly statuses: { value: SessionStatus | null; label: string }[] = [
    { value: null, label: 'Svi' },
    { value: 'ACTIVE', label: 'Aktivna' },
    { value: 'EXPIRED', label: 'Istekla' },
    { value: 'PENDING_PAYMENT', label: 'Čeka plaćanje' },
    { value: 'CANCELLED', label: 'Otkazana' },
  ];

  protected readonly money = money;
  protected readonly date = date;
  protected readonly time = time;
  protected readonly timeSec = timeSec;
  protected readonly monthLabel = monthLabel;
  protected readonly methodLabel = methodLabel;
  protected readonly SESSION_STATUS = SESSION_STATUS;
  protected readonly paymentBadge = paymentBadge;
  protected readonly PURPOSE_LABEL = PURPOSE_LABEL;

  async ngOnInit() {
    try {
      this.vehicles.set(await this.api.vehicles());
    } catch {
      // filter vozila je opcion
    }
    await this.load();
  }

  protected setFilter(change: Partial<HistoryFilter>) {
    this.filter.update((f) => ({ ...f, ...change, page: change.page ?? 0 }));
    this.expanded.set(null);
    this.load();
  }

  protected toggle(id: string) {
    this.expanded.set(this.expanded() === id ? null : id);
  }

  protected periodText(s: ParkingSession): string {
    switch (s.status) {
      case 'CANCELLED':
        return s.payments?.some((p) => p.refundStatus) ? 'Sesija nije aktivirana · refundacija' : 'Plaćanje nije uspelo';
      case 'PENDING_PAYMENT':
        return 'Čeka potvrdu plaćanja';
      default:
        return `${time(s.startsAt)} – ${time(s.endsAt)}`;
    }
  }

  protected eventLabel(e: StatusLogEntry): string {
    return EVENT_LABELS[e.event] ?? e.event;
  }

  protected actorLabel(e: StatusLogEntry): string {
    return ACTOR_LABELS[e.actor];
  }

  protected eventColor(e: StatusLogEntry): string {
    if (e.event === 'PAYMENT_INITIATED' || e.event === 'REFUND_REQUIRED') return '#2950A8';
    return STATUS_COLORS[e.toStatus];
  }

  private async load() {
    this.loading.set(true);
    try {
      this.data.set(await this.api.history(this.filter()));
      this.error.set(null);
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.loading.set(false);
    }
  }
}
