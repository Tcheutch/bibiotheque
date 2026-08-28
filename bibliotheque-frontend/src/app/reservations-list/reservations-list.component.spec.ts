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

  describe('phase 8 : annulation', () => {

    it('demande confirmation avant d\'émettre annuler ; refuser la confirmation n\'émet rien', () => {
      spyOn(window, 'confirm').and.returnValue(false);
      spyOn(component.annuler, 'emit');

      component.onAnnuler(42);

      expect(window.confirm).toHaveBeenCalled();
      expect(component.annuler.emit).not.toHaveBeenCalled();
    });

    it('émet annuler(id) uniquement si la confirmation est acceptée', () => {
      spyOn(window, 'confirm').and.returnValue(true);
      spyOn(component.annuler, 'emit');

      component.onAnnuler(42);

      expect(component.annuler.emit).toHaveBeenCalledWith(42);
    });

    it("n'affiche aucun bloc erreur d'annulation quand erreurAnnulation est null", () => {
      component.erreurAnnulation = null;
      fixture.detectChanges();

      const el: HTMLElement = fixture.nativeElement;
      expect(el.querySelector('.alert-danger')).toBeNull();
    });

    it('affiche le message serveur tel quel pour un refus 409 (RG-05/RG-06)', () => {
      component.erreurAnnulation = 'RG-05 : seules les réservations EN_ATTENTE ou DISPONIBLE peuvent être annulées.';
      fixture.detectChanges();

      const el: HTMLElement = fixture.nativeElement;
      expect(el.querySelector('.alert-danger')!.textContent).toContain('RG-05');
    });
  });
});
