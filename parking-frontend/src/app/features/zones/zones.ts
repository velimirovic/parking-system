import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { Zone } from '../../core/models';
import { ParkingApi } from '../../core/services/parking-api.service';
import { dayName, errorMessage, money, rate } from '../../shared/format';

/** P03 - zone, cenovnici, radno vreme naplate, pravila i lokacije. */
@Component({
  selector: 'app-zones',
  imports: [RouterLink],
  templateUrl: './zones.html',
  styleUrl: './zones.scss',
})
export class Zones implements OnInit {
  private readonly api = inject(ParkingApi);

  protected readonly zones = signal<Zone[]>([]);
  protected readonly selectedId = signal<number | null>(null);
  protected readonly query = signal('');
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly filtered = computed(() => {
    const q = this.query().trim().toLowerCase();
    if (!q) return this.zones();
    return this.zones().filter(
      (z) =>
        z.name.toLowerCase().includes(q) ||
        z.area.toLowerCase().includes(q) ||
        z.locations.some((l) => l.name.toLowerCase().includes(q) || (l.address ?? '').toLowerCase().includes(q)),
    );
  });

  protected readonly selected = computed(() => {
    const list = this.filtered();
    return list.find((z) => z.id === this.selectedId()) ?? list[0] ?? null;
  });

  protected readonly locationCount = computed(() => this.zones().reduce((n, z) => n + z.locations.length, 0));

  /** Dani bez ijedne tarife = bez naplate (npr. nedelja u zoni 1). */
  protected readonly freeDays = computed(() => {
    const z = this.selected();
    if (!z) return '';
    const days = [1, 2, 3, 4, 5, 6, 7].filter((d) => !z.tariffs.some((t) => d >= t.dayFrom && d <= t.dayTo));
    return days.map(dayName).join(', ');
  });

  protected readonly money = money;
  protected readonly rate = rate;

  async ngOnInit() {
    try {
      const zones = await this.api.zones();
      this.zones.set(zones);
      this.selectedId.set(zones[0]?.id ?? null);
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.loading.set(false);
    }
  }

  protected select(zone: Zone) {
    this.selectedId.set(zone.id);
  }

  protected extensions(z: Zone): string {
    if (!z.extensionAllowed) return 'Nije dozvoljeno';
    return z.maxExtensions == null ? 'Neograničeno' : `do ${z.maxExtensions}×`;
  }

  protected coords(z: Zone): string {
    if (z.latitude == null || z.longitude == null) return '';
    return ` · ${Number(z.latitude).toFixed(4)}° N, ${Number(z.longitude).toFixed(4)}° E`;
  }
}
