import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';

import { ReservationFormComponent } from './reservation-form.component';

describe('ReservationFormComponent', () => {
  let component: ReservationFormComponent;
  let fixture: ComponentFixture<ReservationFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FormsModule],
      declarations: [ ReservationFormComponent ]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ReservationFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it("n'émet rien tant que les deux champs ne sont pas renseignés", () => {
    spyOn(component.creer, 'emit');

    component.livreId = undefined;
    component.adherentId = 2;
    component.onSubmit();

    expect(component.creer.emit).not.toHaveBeenCalled();
  });

  it('émet uniquement livreId et adherentId une fois les deux champs renseignés', () => {
    spyOn(component.creer, 'emit');

    component.livreId = 1;
    component.adherentId = 2;
    component.onSubmit();

    expect(component.creer.emit).toHaveBeenCalledWith({ livreId: 1, adherentId: 2 });
  });

  it("onSubmit n'efface pas la sélection lui-même (seul un 201 confirmé le fait)", () => {
    component.livreId = 1;
    component.adherentId = 2;
    component.onSubmit();

    expect(component.livreId).toBe(1);
    expect(component.adherentId).toBe(2);
    expect(component.succesVisible).toBeFalse();
  });

  it("un changement de reinitialiser (201 confirmé par le conteneur) vide les champs et affiche le succès", () => {
    component.livreId = 1;
    component.adherentId = 2;

    component.reinitialiser = 1;
    component.ngOnChanges({
      reinitialiser: { currentValue: 1, previousValue: 0, firstChange: false, isFirstChange: () => false },
    } as any);

    expect(component.livreId).toBeUndefined();
    expect(component.adherentId).toBeUndefined();
    expect(component.succesVisible).toBeTrue();
  });

  it("ne montre pas de succès sur le tout premier changement (firstChange) de reinitialiser", () => {
    component.ngOnChanges({
      reinitialiser: { currentValue: 0, previousValue: undefined, firstChange: true, isFirstChange: () => true },
    } as any);

    expect(component.succesVisible).toBeFalse();
  });

  it('modifier un champ après un succès masque le message de succès', () => {
    component.succesVisible = true;

    component.onLivreIdChange(3);

    expect(component.succesVisible).toBeFalse();
    expect(component.livreId).toBe(3);
  });

  describe('phase 7 : affichage du refus métier', () => {

    it("n'affiche aucun bloc erreur quand erreurCreation est null", () => {
      component.erreurCreation = null;
      fixture.detectChanges();

      const el: HTMLElement = fixture.nativeElement;
      expect(el.querySelector('.alert-danger')).toBeNull();
    });

    it('affiche le message du serveur tel quel pour un 409 (RG-01/02/03), sans liste de détail par champ', () => {
      component.erreurCreation = 'RG-01 : le livre doit être indisponible pour être réservé.';
      component.erreursChamps = null;
      fixture.detectChanges();

      const el: HTMLElement = fixture.nativeElement;
      const bloc = el.querySelector('.alert-danger')!;
      expect(bloc.textContent).toContain('RG-01 : le livre doit être indisponible pour être réservé.');
      expect(bloc.querySelector('ul')).toBeNull();
    });

    it('affiche le message ET le détail par champ pour un 400 de validation', () => {
      component.erreurCreation = 'Validation échouée.';
      component.erreursChamps = { adherentId: 'adherentId est obligatoire' };
      fixture.detectChanges();

      const el: HTMLElement = fixture.nativeElement;
      const bloc = el.querySelector('.alert-danger')!;
      expect(bloc.textContent).toContain('Validation échouée.');
      expect(bloc.textContent).toContain('adherentId est obligatoire');
    });

    it('affiche le message adapté pour un 404, sans liste de détail par champ', () => {
      component.erreurCreation = "Livre avec l'identifiant 999 introuvable.";
      component.erreursChamps = null;
      fixture.detectChanges();

      const el: HTMLElement = fixture.nativeElement;
      const bloc = el.querySelector('.alert-danger')!;
      expect(bloc.textContent).toContain("Livre avec l'identifiant 999 introuvable.");
      expect(bloc.querySelector('ul')).toBeNull();
    });
  });
});
