import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';

import { ReservationService } from './reservation.service';
import { ReservationStatus } from '../_model/reservation';

describe('ReservationService', () => {
  let service: ReservationService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule]
    });
    service = TestBed.inject(ReservationService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('« tous » : liste sans paramètre statut', () => {
    service.listerReservations().subscribe();

    const req = httpMock.expectOne(r => r.url === 'http://localhost:8080/api/reservations');
    expect(req.request.params.has('statut')).toBeFalse();
    req.flush([]);
  });

  it('un statut précis : ajoute le paramètre statut avec la valeur exacte de l\'enum', () => {
    service.listerReservations(ReservationStatus.ANNULEE).subscribe();

    const req = httpMock.expectOne(r => r.url === 'http://localhost:8080/api/reservations');
    expect(req.request.params.get('statut')).toBe('ANNULEE');
    req.flush([]);
  });

  it('annulerReservation appelle PATCH /api/reservations/{id}/annuler', () => {
    service.annulerReservation(5).subscribe();

    const req = httpMock.expectOne('http://localhost:8080/api/reservations/5/annuler');
    expect(req.request.method).toBe('PATCH');
    req.flush({});
  });

  it('creerReservation envoie uniquement livreId et adherentId', () => {
    service.creerReservation({ livreId: 3, adherentId: 4 }).subscribe();

    const req = httpMock.expectOne('http://localhost:8080/api/reservations');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ livreId: 3, adherentId: 4 });
    req.flush({});
  });
});
