import { Component, inject, input, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Vehicle } from '../../core/models';
import { AuthService } from '../../core/services/auth.service';
import { ParkingApi } from '../../core/services/parking-api.service';
import { errorMessage, problemOf } from '../../shared/format';

interface VehicleForm {
  plate: string;
  brand: string;
  model: string;
  makeDefault: boolean;
}

const EMPTY_FORM: VehicleForm = { plate: '', brand: '', model: '', makeDefault: false };

/** P06 - vozila korisnika i vreme obavestenja pre isteka parkinga. */
@Component({
  selector: 'app-vehicles',
  imports: [FormsModule, RouterLink],
  templateUrl: './vehicles.html',
  styleUrl: './vehicles.scss',
})
export class Vehicles implements OnInit {
  private readonly api = inject(ParkingApi);
  protected readonly auth = inject(AuthService);

  /** ?dobrodosli=1 posle registracije */
  readonly dobrodosli = input<string>();

  protected readonly vehicles = signal<Vehicle[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly info = signal<string | null>(null);
  protected readonly busy = signal(false);
  protected readonly fieldErrors = signal<Record<string, string>>({});

  /** null = dodavanje novog vozila, inace ID vozila koje se menja */
  protected readonly editingId = signal<string | null>(null);
  protected readonly form = signal<VehicleForm>({ ...EMPTY_FORM });

  protected readonly notifyOptions = [5, 10, 15, 30];
  protected readonly savingNotify = signal(false);

  async ngOnInit() {
    await this.load();
  }

  protected update(change: Partial<VehicleForm>) {
    this.form.update((f) => ({ ...f, ...change }));
  }

  protected edit(v: Vehicle) {
    this.editingId.set(v.id);
    this.form.set({ plate: v.plate, brand: v.brand ?? '', model: v.model ?? '', makeDefault: v.isDefault });
    this.fieldErrors.set({});
    this.error.set(null);
  }

  protected cancelEdit() {
    this.editingId.set(null);
    this.form.set({ ...EMPTY_FORM });
    this.fieldErrors.set({});
  }

  protected async save() {
    if (this.busy()) return;
    const f = this.form();
    this.error.set(null);
    this.info.set(null);
    this.fieldErrors.set({});
    if (!f.plate.trim()) {
      this.fieldErrors.set({ plate: 'Registarska oznaka je obavezna.' });
      return;
    }
    this.busy.set(true);
    const request = {
      plate: f.plate.trim(),
      brand: f.brand.trim() || null,
      model: f.model.trim() || null,
      makeDefault: f.makeDefault,
    };
    try {
      const id = this.editingId();
      if (id) {
        await this.api.updateVehicle(id, request);
        this.info.set('Vozilo je izmenjeno.');
      } else {
        await this.api.addVehicle(request);
        this.info.set('Vozilo je sačuvano.');
      }
      this.cancelEdit();
      await this.load();
    } catch (e) {
      const problem = problemOf(e);
      if (problem.code === 'INVALID_PLATE' || problem.code === 'PLATE_EXISTS') {
        this.fieldErrors.set({ plate: problem.detail });
      } else if (problem.fields) {
        this.fieldErrors.set(problem.fields);
      } else {
        this.error.set(problem.detail);
      }
    } finally {
      this.busy.set(false);
    }
  }

  protected async remove(v: Vehicle) {
    if (this.busy()) return;
    if (!confirm(`Ukloniti vozilo ${v.plate}? Ostaće vidljivo u istoriji parkiranja.`)) return;
    this.busy.set(true);
    this.error.set(null);
    this.info.set(null);
    try {
      await this.api.deleteVehicle(v.id);
      if (this.editingId() === v.id) this.cancelEdit();
      this.info.set(`Vozilo ${v.plate} je uklonjeno.`);
      await this.load();
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }

  protected async setNotify(minutes: number) {
    if (this.savingNotify() || this.auth.user()?.notifyBeforeMinutes === minutes) return;
    this.savingNotify.set(true);
    try {
      await this.auth.setNotifyBefore(minutes);
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.savingNotify.set(false);
    }
  }

  private async load() {
    try {
      this.vehicles.set(await this.api.vehicles());
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.loading.set(false);
    }
  }
}
