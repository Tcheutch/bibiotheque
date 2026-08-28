import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';

import { ReservationsListComponent } from './reservations-list.component';
import { ReservationStatus } from '../_model/reservation';

describe('ReservationsListComponent', () => {
  let component: ReservationsListComponent;
  let fixture: ComponentFixture<ReservationsListComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FormsModule],
      declarations: [ ReservationsListComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ReservationsListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('affiche le bouton Annuler uniquement pour EN_ATTENTE et DISPONIBLE', () => {
    expect(component.estAnnulable(ReservationStatus.EN_ATTENTE)).toBeTrue();
    expect(component.estAnnulable(ReservationStatus.DISPONIBLE)).toBeTrue();
    expect(component.estAnnulable(ReservationStatus.ANNULEE)).toBeFalse();
    expect(component.estAnnulable(ReservationStatus.EXPIREE)).toBeFalse();
    expect(component.estAnnulable(ReservationStatus.HONOREE)).toBeFalse();
  });
});
