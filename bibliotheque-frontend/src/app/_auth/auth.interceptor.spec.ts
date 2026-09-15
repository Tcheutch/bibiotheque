import { TestBed } from '@angular/core/testing';
import { HttpClient, HttpErrorResponse, HTTP_INTERCEPTORS } from '@angular/common/http';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { Router } from '@angular/router';

import { UserAuthService } from '../_service/user-auth.service';
import { AuthInterceptor } from './auth.interceptor';

describe('AuthInterceptor', () => {
  let httpClient: HttpClient;
  let httpMock: HttpTestingController;
  let router: jasmine.SpyObj<Router>;
  let userAuthService: UserAuthService;

  beforeEach(() => {
    const routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        { provide: HTTP_INTERCEPTORS, useClass: AuthInterceptor, multi: true },
        { provide: Router, useValue: routerSpy },
      ],
    });

    httpClient = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router) as jasmine.SpyObj<Router>;
    userAuthService = TestBed.inject(UserAuthService);
    spyOn(userAuthService, 'clear');
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('sur un 401, vide la session et redirige vers /login avec le message du serveur tel quel', () => {
    httpClient.get('/test').subscribe({
      next: () => fail('la requête aurait dû échouer'),
      error: () => {},
    });

    httpMock.expectOne('/test').flush(
      { message: 'Votre session a expiré. Veuillez vous reconnecter.', errors: {} },
      { status: 401, statusText: 'Unauthorized' }
    );

    expect(userAuthService.clear).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(
      ['/login'], { state: { message: 'Votre session a expiré. Veuillez vous reconnecter.' } }
    );
  });

  it('sur un 401 sans message exploitable, redirige vers /login avec un message de repli', () => {
    httpClient.get('/test').subscribe({
      next: () => fail('la requête aurait dû échouer'),
      error: () => {},
    });

    httpMock.expectOne('/test').flush('Non autorisé', { status: 401, statusText: 'Unauthorized' });

    expect(userAuthService.clear).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(
      ['/login'], { state: { message: "Votre session n'est plus valide. Veuillez vous reconnecter." } }
    );
  });

  it('redirige vers /forbidden sur une erreur 403', () => {
    httpClient.get('/test').subscribe({
      next: () => fail('la requête aurait dû échouer'),
      error: () => {},
    });

    httpMock.expectOne('/test').flush('Interdit', { status: 403, statusText: 'Forbidden' });

    expect(router.navigate).toHaveBeenCalledWith(['/forbidden']);
    expect(userAuthService.clear).not.toHaveBeenCalled();
  });

  it('relance le HttpErrorResponse d\'origine pour un 409, sans le remplacer par un message générique', () => {
    let erreurRecue: HttpErrorResponse | undefined;
    const corpsServeur = {
      message: "RG-01 : le livre doit être indisponible pour être réservé.",
      errors: {},
    };

    httpClient.post('/api/reservations', {}).subscribe({
      next: () => fail('la requête aurait dû échouer'),
      error: (err) => (erreurRecue = err),
    });

    httpMock.expectOne('/api/reservations').flush(corpsServeur, { status: 409, statusText: 'Conflict' });

    expect(erreurRecue instanceof HttpErrorResponse).toBeTrue();
    expect(erreurRecue?.status).toBe(409);
    expect(erreurRecue?.error).toEqual(corpsServeur);
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('relance aussi le HttpErrorResponse d\'origine pour un 400 (erreur générique non substituée)', () => {
    let erreurRecue: HttpErrorResponse | undefined;

    httpClient.post('/api/reservations', {}).subscribe({
      next: () => fail('la requête aurait dû échouer'),
      error: (err) => (erreurRecue = err),
    });

    httpMock.expectOne('/api/reservations').flush(
      { message: 'Validation échouée.', errors: { adherentId: 'adherentId est obligatoire' } },
      { status: 400, statusText: 'Bad Request' }
    );

    expect(erreurRecue instanceof HttpErrorResponse).toBeTrue();
    expect(erreurRecue?.status).toBe(400);
    expect(erreurRecue?.error?.message).toBe('Validation échouée.');
    expect(router.navigate).not.toHaveBeenCalled();
  });
});
