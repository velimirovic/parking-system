import { Component, computed, DestroyRef, inject, input, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Quote, Vehicle, Zone } from '../../core/models';
import { ParkingApi } from '../../core/services/parking-api.service';
import { durationLabel, errorMessage, money, newRequestId, problemOf, rate, time } from '../../shared/format';

/** Trajanja koja se nude dok obracun jos nije stigao (server vraca tacnu listu po zoni). */
const DEFAULT_DURATIONS = [15, 30, 60, 90, 120, 180, 240];

/**
 * P04 - nova parking sesija: izbor zone, vozila i trajanja, obracun uzivo i prelazak na placanje.
 * Iznos i pravila racuna server (POST /sessions/quote); frontend nikad sam ne odredjuje cenu.
 */
@Component({
  selector: 'app-new-session',
  imports: [FormsModule, RouterLink],
  templateUrl: './new-session.html',
  styleUrl: './new-session.scss',
})
export class NewSession implements OnInit {
  private readonly api = inject(ParkingApi);

  /** query parametri: ?zona=1&vozilo=...&trajanje=60 (npr. sa stranice zona ili "Pokušaj ponovo") */
  readonly zona = input<string>();
  readonly vozilo = input<string>();
  readonly trajanje = input<string>();

  protected readonly zones = signal<Zone[]>([]);
  protected readonly vehicles = signal<Vehicle[]>([]);
  protected readonly loading = signal(true);
  protected readonly loadError = signal<string | null>(null);

  protected readonly zoneId = signal<number | null>(null);
  protected readonly vehicleId = signal<string | null>(null);
  protected readonly locationId = signal<number | null>(null);
  protected readonly duration = signal(60);

  protected readonly quote = signal<Quote | null>(null);
  protected readonly quoting = signal(false);
  protected readonly submitting = signal(false);
  protected readonly submitError = signal<string | null>(null);
  protected readonly canRetry = signal(false);

  /** ID zahteva: isti za ponovljeni klik/pokusaj, nov cim korisnik promeni izbor. */
  private requestId = newRequestId();
  private quoteSeq = 0;

  protected readonly zone = computed(() => this.zones().find((z) => z.id === this.zoneId()) ?? null);
  protected readonly vehicle = computed(() => this.vehicles().find((v) => v.id === this.vehicleId()) ?? null);

  protected readonly durations = computed(() => {
    const q = this.quote();
    if (q && q.zoneId === this.zoneId()) return q.durations;
    const z = this.zone();
    return DEFAULT_DURATIONS.filter((d) => !z || (d >= z.minDurationMinutes && d <= z.maxDurationMinutes)).map(
      (minutes) => ({ minutes, available: true }),
    );
  });

  protected readonly canPay = computed(() => {
    const q = this.quote();
    return !!q && q.valid && q.amount != null && !!this.vehicleId() && !this.quoting() && !this.submitting();
  });

  protected readonly money = money;
  protected readonly rate = rate;
  protected readonly time = time;
  protected readonly durationLabel = durationLabel;

  constructor() {
    // vreme tece - obracun (pocetak, kraj, radno vreme) se osvezava svakih 30 s
    const timer = setInterval(() => this.refreshQuote(), 30_000);
    inject(DestroyRef).onDestroy(() => clearInterval(timer));
  }

  async ngOnInit() {
    try {
      const [zones, vehicles] = await Promise.all([this.api.zones(), this.api.vehicles()]);
      this.zones.set(zones.filter((z) => z.active));
      this.vehicles.set(vehicles);

      const requestedZone = Number(this.zona());
      const zone = zones.find((z) => z.id === requestedZone) ?? zones.find((z) => z.chargingNow) ?? zones[0];
      this.zoneId.set(zone?.id ?? null);

      const requestedVehicle = vehicles.find((v) => v.id === this.vozilo());
      this.vehicleId.set((requestedVehicle ?? vehicles.find((v) => v.isDefault) ?? vehicles[0])?.id ?? null);

      const requestedDuration = Number(this.trajanje());
      if (requestedDuration > 0) this.duration.set(requestedDuration);
      this.fitDuration();
      await this.refreshQuote();
    } catch (e) {
      this.loadError.set(errorMessage(e));
    } finally {
      this.loading.set(false);
    }
  }

  protected pickZone(zone: Zone) {
    if (zone.id === this.zoneId()) return;
    this.zoneId.set(zone.id);
    this.locationId.set(null);
    this.fitDuration();
    this.changed();
  }

  protected pickVehicle(vehicle: Vehicle) {
    this.vehicleId.set(vehicle.id);
    this.changed();
  }

  protected pickDuration(minutes: number) {
    this.duration.set(minutes);
    this.changed();
  }

  protected pickLocation(value: string) {
    this.locationId.set(value ? Number(value) : null);
    this.changed();
  }

  protected zoneRate(z: Zone): string {
    return rate(z.currentRate ?? z.baseRatePerHour, z.currency);
  }

  protected zoneRules(z: Zone): string {
    const ext = !z.extensionAllowed
      ? 'bez produženja'
      : z.maxExtensions == null
        ? 'neograničeno'
        : `do ${z.maxExtensions} prod.`;
    return `Max ${z.maxDurationMinutes} min · ${ext}`;
  }

  protected checkIcon(state: string): string {
    return state === 'OK' ? 'ok' : state === 'FAIL' ? 'fail' : 'idle';
  }

  /** Kreira nalog i preusmerava korisnika na checkout PSP-a. */
  protected async pay() {
    const q = this.quote();
    const vehicleId = this.vehicleId();
    const zoneId = this.zoneId();
    if (!q || !q.valid || q.amount == null || !vehicleId || zoneId == null || this.submitting()) return;
    this.submitting.set(true);
    this.submitError.set(null);
    this.canRetry.set(false);
    try {
      const res = await this.api.createSession({
        zoneId,
        vehicleId,
        locationId: this.locationId(),
        durationMinutes: this.duration(),
        expectedAmount: q.amount,
        requestId: this.requestId,
      });
      if (res.redirectUrl) {
        window.location.href = res.redirectUrl;
        return; // ostaje "submitting" dok se stranica ne promeni
      }
      this.submitError.set('Nalog je kreiran, ali plaćanje nije moguće pokrenuti. Proverite status na pregledu.');
    } catch (e) {
      const problem = problemOf(e);
      if (problem.code === 'PRICE_CHANGED' || problem.code === 'RULE_VIOLATION') {
        this.requestId = newRequestId();
        await this.refreshQuote();
      }
      // PSP nedostupan: nalog je sacuvan, ponovni pokusaj sa istim requestId ga salje ponovo
      this.canRetry.set(problem.code === 'PSP_UNAVAILABLE' || problem.status === 0);
      if (problem.code === 'PSP_REJECTED') this.requestId = newRequestId();
      this.submitError.set(errorMessage(e));
    }
    this.submitting.set(false);
  }

  private changed() {
    this.requestId = newRequestId();
    this.submitError.set(null);
    this.canRetry.set(false);
    this.refreshQuote();
  }

  /** Trajanje van granica nove zone se svodi na najblize dozvoljeno. */
  private fitDuration() {
    const z = this.zone();
    if (!z) return;
    const d = this.duration();
    if (d > z.maxDurationMinutes) this.duration.set(z.maxDurationMinutes);
    if (d < z.minDurationMinutes) this.duration.set(z.minDurationMinutes);
  }

  private async refreshQuote() {
    const zoneId = this.zoneId();
    if (zoneId == null || this.submitting()) return;
    const seq = ++this.quoteSeq;
    this.quoting.set(true);
    try {
      const q = await this.api.quote({
        zoneId,
        vehicleId: this.vehicleId(),
        locationId: this.locationId(),
        durationMinutes: this.duration(),
      });
      if (seq === this.quoteSeq) this.quote.set(q);
    } catch (e) {
      if (seq === this.quoteSeq) {
        this.quote.set(null);
        this.submitError.set(errorMessage(e));
      }
    } finally {
      if (seq === this.quoteSeq) this.quoting.set(false);
    }
  }
}
