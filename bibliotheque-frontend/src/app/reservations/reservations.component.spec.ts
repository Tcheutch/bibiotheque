import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { NO_ERRORS_SCHEMA } from '@angular/core';

import { ReservationsComponent } from './reservations.component';
import { ReservationStatus } from '../_model/reservation';

describe('ReservationsComponent', () => {
  let component: ReservationsComponent;
  let fixture: ComponentFixture<ReservationsComponent>;
  let httpMock: HttpTestingController;

  const RESERVATIONS_URL = 'http://localhost:8080/api/reservations';

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      declarations: [ ReservationsComponent ],
      schemas: [NO_ERRORS_SCHEMA]
    })
    .compileComponents();

    fixture = TestBed.createComponent(ReservationsComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();

    // ngOnInit déclenche livres + adhérents + réservations : on les solde ici
    // pour que chaque test ne s'occupe que des appels qui l'intéressent.
    httpMock.expectOne('http://localhost:8080/admin/books').flush([]);
    httpMock.expectOne('http://localhost:8080/admin/users').flush([]);
    httpMock.expectOne(r => r.url === RESERVATIONS_URL).flush([]);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('résout les libellés livre/adhérent sans toucher au DTO backend', () => {
    component.livres = [{ bookId: 1, bookName: 'Le Petit Prince', bookAuthor: '', bookGenre: '', noOfCopies: 0 }];
    component.adherents = [{ userId: 2, username: 'a2', name: 'A2 - Test quota', password: '', role: [] }];
    component.reservations = [{
      id: 10, livreId: 1, adherentId: 2,
      dateReservation: new Date(), dateExpiration: new Date(),
      statut: 'EN_ATTENTE' as any
    }];

    const [affichage] = component.reservationsAffichables;

    expect(affichage.titreLivre).toBe('Le Petit Prince');
    expect(affichage.nomAdherent).toBe('A2 - Test quota');
    expect((affichage as any).id).toBe(10);
  });

  it('un changement de filtre relance une vraie requête HTTP avec le bon paramètre statut', () => {
    component.onFiltreChange(ReservationStatus.ANNULEE);

    const req = httpMock.expectOne(r => r.url === RESERVATIONS_URL);
    expect(req.request.params.get('statut')).toBe('ANNULEE');
    req.flush([]);
  });

  it('revenir à « tous » relance une requête sans paramètre statut (jamais statut=tous)', () => {
    component.onFiltreChange(ReservationStatus.ANNULEE);
    httpMock.expectOne(r => r.url === RESERVATIONS_URL).flush([]);

    component.onFiltreChange(undefined);

    const req = httpMock.expectOne(r => r.url === RESERVATIONS_URL);
    expect(req.request.params.has('statut')).toBeFalse();
    req.flush([]);
  });

  it('la création réussie recharge la liste (nouvel appel, pas de fusion locale)', () => {
    component.onCreerReservation({ livreId: 1, adherentId: 2 });

    const creation = httpMock.expectOne(r => r.url === RESERVATIONS_URL && r.method === 'POST');
    expect(creation.request.body).toEqual({ livreId: 1, adherentId: 2 });
    creation.flush({});

    const rechargement = httpMock.expectOne(r => r.url === RESERVATIONS_URL && r.method === 'GET');
    expect(rechargement.request.method).toBe('GET');
    rechargement.flush([]);
  });

  it('une annulation réussie recharge la liste (nouvel appel, pas de mutation locale)', () => {
    component.onAnnulerReservation(7);

    const annulation = httpMock.expectOne(`${RESERVATIONS_URL}/7/annuler`);
    expect(annulation.request.method).toBe('PATCH');
    annulation.flush({});

    const rechargement = httpMock.expectOne(r => r.url === RESERVATIONS_URL && r.method === 'GET');
    expect(rechargement.request.method).toBe('GET');
    rechargement.flush([]);
  });
});
