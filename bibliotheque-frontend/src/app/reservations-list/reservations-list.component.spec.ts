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

  it('affiche les libellés résolus et les dates formatées, pas les identifiants bruts', () => {
    // Format exact renvoyé par le backend (Instant Jackson : fraction de seconde à 9 chiffres).
    component.reservations = [{
      id: 42,
      livreId: 7,
      adherentId: 9,
      dateReservation: '2026-08-28T10:01:27.261237411Z' as any,
      dateExpiration: '2026-09-04T10:01:27.261237411Z' as any,
      statut: ReservationStatus.EN_ATTENTE,
      titreLivre: 'Le Petit Prince',
      nomAdherent: 'A2 - Test quota',
    }];
    fixture.detectChanges();

    const texteLigne = (fixture.nativeElement as HTMLElement).querySelector('tbody tr')!.textContent!;

    expect(texteLigne).toContain('Le Petit Prince');
    expect(texteLigne).toContain('A2 - Test quota');
    // Date mise en forme (UTC, indépendante du fuseau de la machine qui exécute le test),
    // jamais le timestamp ISO brut renvoyé par le serveur.
    expect(texteLigne).toContain('28/08/2026 10:01');
    expect(texteLigne).toContain('04/09/2026 10:01');
    expect(texteLigne).not.toContain('2026-08-28T10:01:27');
  });

  it("n'affiche pas le bouton Annuler pour une réservation ANNULEE", () => {
    component.reservations = [{
      id: 1, livreId: 1, adherentId: 1,
      dateReservation: new Date() as any, dateExpiration: new Date() as any,
      statut: ReservationStatus.ANNULEE,
      titreLivre: 'X', nomAdherent: 'Y',
    }];
    fixture.detectChanges();

    const bouton = (fixture.nativeElement as HTMLElement).querySelector('tbody tr button');
    expect(bouton).toBeNull();
  });

  it('liste vide : message explicite, en-têtes de colonnes conservés', () => {
    component.reservations = [];
    fixture.detectChanges();

    const el: HTMLElement = fixture.nativeElement;
    const entetes = Array.from(el.querySelectorAll('thead th')).map(th => th.textContent!.trim());

    expect(entetes).toEqual(['Livre', 'Adhérent', 'Statut', 'Date de réservation', "Date d'expiration", 'Action']);
    expect(el.querySelector('tbody')!.textContent).toContain('Aucune réservation');
    expect(el.querySelectorAll('tbody tr').length).toBe(1);
  });
});
