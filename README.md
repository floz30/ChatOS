# ChatOS
> Réalisé par : Florian CAHAY - Michel IP

ChatOS est un service de discussions qui permet de mettre en relation TCP directe les utilisateurs sans divulguer leur 
adresse IP.

## Commandes Ant disponibles

Créer les répertoires nécessaires pour la compilation et la Javadoc :
```bash
ant init
```
Compile les fichiers sources Java et génère les exécutables `.jar` :
```bash
ant build
```
Génère la JavaDoc dans le répertoire `/documentation` :
```bash
ant javadoc
```
Supprime tous les répertoires dédiés à la compilation (ceux créés avec `ant init`) :
```bash
ant clean
```
Supprime le répertoire `/jar` contenant les exécutables :
```bash
ant clean-jar
```
Supprime le répertoire `/documentation` contenant la JavaDoc :
```bash
ant clean-javadoc
```

## Exécution

### Serveur

Le serveur prend deux arguments :
- un entier correspondant à son port public
- un entier correspondant à son port privé

Note : le port public et le port privé ne peuvent pas être identiques.
```bash
java -jar server.jar <port_public> <port_prive>
```

### Client

Le client prend 4 arguments :
- une chaîne de caractères correspondant à son pseudo
- une chaîne de caractères correspondant à l'adresse IP du serveur
- un entier correspondant au port public du serveur
- une chaîne de caractères correspondant au répertoire courant utilisé pour les connexions privées

```bash
java -jar client.jar <pseudo> <adresse_ip> <port> <repertoire>
```