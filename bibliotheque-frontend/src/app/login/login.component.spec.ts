import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { FormsModule } from '@angular/forms';
import { Navigation, Router } from '@angular/router';

import { LoginComponent } from './login.component';

describe('LoginComponent', () => {
  let component: LoginComponent;
  let fixture: ComponentFixture<LoginComponent>;

  // Le message arrive par l'état de la navigation vers /login (posé par
  // AuthInterceptor sur un 401) : on simule donc la navigation en cours.
  async function creer(state?: { [cle: string]: any }) {
    const routerStub = {
      navigate: jasmine.createSpy('navigate'),
      getCurrentNavigation: () => (state ? ({ extras: { state } } as unknown as Navigation) : null),
    };

    await TestBed.configureTestingModule({
      declarations: [ LoginComponent ],
      imports: [ HttpClientTestingModule, FormsModule ],
      providers: [ { provide: Router, useValue: routerStub } ],
    })
    .compileComponents();

    fixture = TestBed.createComponent(LoginComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  it('should create', async () => {
    await creer();
    expect(component).toBeTruthy();
  });

  it('affiche tel quel le message transmis par la redirection (ex. session expirée)', async () => {
    await creer({ message: 'Votre session a expiré. Veuillez vous reconnecter.' });

    const alerte: HTMLElement | null = fixture.nativeElement.querySelector('.alert');
    expect(alerte?.textContent).toContain('Votre session a expiré. Veuillez vous reconnecter.');
  });

  it("n'affiche aucune alerte quand on arrive sur /login sans message", async () => {
    await creer();

    expect(fixture.nativeElement.querySelector('.alert')).toBeNull();
  });
});
